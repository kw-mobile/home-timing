package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.domain.Invite;
import com.kwmobile.hometiming.domain.InviteCodes;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;

import static com.kwmobile.hometiming.data.firestore.HouseTasks.fail;
import static com.kwmobile.hometiming.data.firestore.HouseTasks.translate;

/** Active invitation lifecycle; failed attempts reserve their rate limit before the action. */
final class HouseInvitations {
    private static final int INVITE_ATTEMPTS_PER_MINUTE = 10;

    @Nullable
    private final FirebaseFirestore db;
    private final Clock clock;
    private final Supplier<String> currentUid;
    private final HouseTasks tasks;
    private final SecureRandom random = new SecureRandom();

    HouseInvitations(@Nullable FirebaseFirestore db, Clock clock, Supplier<String> currentUid, HouseTasks tasks) {
        this.db = db;
        this.clock = clock;
        this.currentUid = currentUid;
        this.tasks = tasks;
    }

    Task<Invite> currentInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(currentUid(), houseId);
        if (scope == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        Task<Invite> task = houseRef(firestore, houseId).get(Source.SERVER).onSuccessTask(houseSnapshot -> {
            tasks.requireCurrent(scope);
            House house = readHouse(houseSnapshot);
            if (house == null || house.activeInviteCode == null) {
                return Tasks.forResult(null);
            }
            Task<Void> access = house.isOwner(scope.uid) ? Tasks.forResult(null)
                    : reserveInviteAccess(scope, house.activeInviteCode, "LOOKUP");
            return access.onSuccessTask(ignored -> inviteRef(firestore, house.activeInviteCode).get(Source.SERVER))
                    .onSuccessTask(snapshot -> {
                tasks.requireCurrent(scope);
                Invite invite = readInvite(snapshot);
                return Tasks.forResult(invite != null && invite.houseId.equals(houseId) && invite.isLive(clock.instant())
                        ? invite : null);
            });
        });
        return tasks.scoped(translate(task), scope);
    }

    Task<Invite> issueInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        String code = InviteCodes.generate(random);
        Instant now = clock.instant();
        Task<String> issued = reserveInviteAccess(scope, code, "ISSUE").onSuccessTask(ignored ->
                tasks.transaction(firestore, scope, tx -> {
            House current = requireHouse(tx.get(house));
            if (!current.isOwner(uid)) {
                throw new HouseException.Rejection(HouseError.NOT_OWNER);
            }
            Invite existing = readInvite(tx.get(inviteRef(firestore, code)));
            if (existing != null && existing.isLive(now)) {
                throw new HouseException.Rejection(HouseError.CODE_REJECTED);
            }
            String previous = current.activeInviteCode;
            Invite previousInvite = previous == null || previous.equals(code)
                    ? null : readInvite(tx.get(inviteRef(firestore, previous)));
            tx.set(inviteRef(firestore, code), HouseDocuments.inviteFields(houseId, uid, FieldValue.serverTimestamp()));
            tx.update(house, Schema.Houses.ACTIVE_INVITE_CODE, code);
            if (previousInvite != null && previousInvite.houseId.equals(houseId)) {
                tx.delete(inviteRef(firestore, previous));
            }
            return code;
        }));
        Task<Invite> task = issued.onSuccessTask(issuedCode -> inviteRef(firestore, issuedCode).get(Source.SERVER)
                .onSuccessTask(snapshot -> {
                    tasks.requireCurrent(scope);
                    Invite invite = readInvite(snapshot);
                    return invite == null ? fail(HouseError.UNKNOWN) : Tasks.forResult(invite);
                }));
        return tasks.scoped(translate(task), scope);
    }

    Task<Void> stopInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        return tasks.scoped(translate(tasks.transaction(firestore, scope, tx -> {
            House current = requireHouse(tx.get(house));
            if (!current.isOwner(uid)) {
                throw new HouseException.Rejection(HouseError.NOT_OWNER);
            }
            if (current.activeInviteCode == null) {
                return null;
            }
            Invite invite = readInvite(tx.get(inviteRef(firestore, current.activeInviteCode)));
            tx.update(house, Schema.Houses.ACTIVE_INVITE_CODE, FieldValue.delete());
            if (invite != null && invite.houseId.equals(houseId)) {
                tx.delete(inviteRef(firestore, current.activeInviteCode));
            }
            return null;
        })), scope);
    }

    /** Reserve independently, so invalid codes and refused joins also consume an attempt. */
    Task<Void> reserveInviteAccess(OperationScope.Token scope, String code, String purpose) {
        DocumentReference access = db.collection(Schema.InviteLookups.COLLECTION).document(scope.uid);
        return tasks.transaction(db, scope, tx -> {
            DocumentSnapshot previous = tx.get(access);
            java.util.Date started = previous.getDate(Schema.InviteLookups.WINDOW_STARTED_AT);
            Long attempts = previous.getLong(Schema.InviteLookups.ATTEMPTS);
            boolean reset = started == null || clock.millis() - started.getTime() >= 60_000;
            if (!reset && attempts != null && attempts >= INVITE_ATTEMPTS_PER_MINUTE) {
                throw new HouseException.Rejection(HouseError.RATE_LIMITED);
            }
            Map<String, Object> fields = new java.util.HashMap<>();
            fields.put(Schema.InviteLookups.CODE, code);
            fields.put(Schema.InviteLookups.PURPOSE, purpose);
            fields.put(Schema.InviteLookups.ATTEMPTS, reset ? 1 : (attempts == null ? 1 : attempts + 1));
            fields.put(Schema.InviteLookups.WINDOW_STARTED_AT,
                    reset ? FieldValue.serverTimestamp() : previous.getTimestamp(Schema.InviteLookups.WINDOW_STARTED_AT));
            fields.put(Schema.InviteLookups.UPDATED_AT, FieldValue.serverTimestamp());
            tx.set(access, fields);
            return null;
        });
    }

    private static DocumentReference houseRef(FirebaseFirestore firestore, String houseId) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId);
    }

    private static DocumentReference inviteRef(FirebaseFirestore firestore, String code) {
        return firestore.collection(Schema.Invites.COLLECTION).document(code);
    }

    @Nullable
    private static House readHouse(DocumentSnapshot snapshot) {
        Map<String, Object> data = snapshot.exists() ? snapshot.getData() : null;
        return data == null ? null : HouseDocuments.toHouse(snapshot.getId(), data);
    }

    private static House requireHouse(DocumentSnapshot snapshot) {
        House house = readHouse(snapshot);
        if (house == null) {
            throw new HouseException.Rejection(HouseError.NOT_FOUND);
        }
        return house;
    }

    /** Null when missing or malformed; server timestamps are converted to an Instant. */
    @Nullable
    static Invite readInvite(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        String houseId = snapshot.getString(Schema.Invites.HOUSE_ID);
        Timestamp createdAt = snapshot.getTimestamp(Schema.Invites.CREATED_AT);
        if (houseId == null || createdAt == null) {
            return null;
        }
        return new Invite(snapshot.getId(), houseId,
                Instant.ofEpochSecond(createdAt.getSeconds(), createdAt.getNanoseconds()));
    }

    @Nullable
    private String currentUid() {
        return currentUid.get();
    }
}
