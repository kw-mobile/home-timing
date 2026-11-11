package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.domain.HouseRules;
import com.kwmobile.hometiming.domain.Invite;
import com.kwmobile.hometiming.domain.InviteCodes;
import com.kwmobile.hometiming.domain.Member;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Family sharing (docs/MVP.md F-14, docs/DATA_MODEL.md 가족 공유): the house and its members,
 * invites, joining, leaving, removing and the region.
 *
 * <p>Every change is one transaction, so it needs the server and fails with
 * {@link HouseError#NETWORK} offline. Non-members never read the house they join: the join reads only
 * the invite and the caller's own user and house. After a join or leave the user's house changes,
 * so screens stop their listeners and call {@code AuthRepository.restoreSession()} for the new one.
 */
public final class HouseRepository {
    public interface HouseListener {
        /** {@code house} is null when it no longer exists or is unreadable. */
        void onHouse(@Nullable House house, boolean fromCache);

        void onError(@NonNull HouseError error);
    }

    public interface MembersListener {
        /** Owner first, then by name. */
        void onMembers(@NonNull List<Member> members, boolean fromCache);

        void onError(@NonNull HouseError error);
    }

    /** Fresh codes tried when the drawn one is still live for another house. */
    static final int CODE_ATTEMPTS = 8;

    private static volatile HouseRepository instance;

    @Nullable
    private final FirebaseFirestore db;
    @Nullable
    private final FirebaseAuth auth;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    private HouseRepository(@Nullable FirebaseFirestore db, @Nullable FirebaseAuth auth, Clock clock) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
    }

    /** Without app/google-services.json every call reports {@link HouseError#NOT_CONFIGURED}. */
    @NonNull
    public static HouseRepository getInstance() {
        HouseRepository local = instance;
        if (local == null) {
            synchronized (HouseRepository.class) {
                local = instance;
                if (local == null) {
                    local = BuildConfig.FIREBASE_CONFIGURED
                            ? new HouseRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance(),
                                    Clock.systemUTC())
                            : new HouseRepository(null, null, Clock.systemUTC());
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---- Reads ----

    @NonNull
    public Subscription listenHouse(String houseId, HouseListener listener) {
        if (db == null) {
            listener.onError(HouseError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        ListenerRegistration registration = houseRef(db, houseId).addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                listener.onError(classify(error));
                return;
            }
            listener.onHouse(readHouse(snapshot), snapshot.getMetadata().isFromCache());
        });
        return registration::remove;
    }

    /** Members of a house; the owner flag comes from {@code ownerId} as the screen last saw it. */
    @NonNull
    public Subscription listenMembers(String houseId, String ownerId, MembersListener listener) {
        if (db == null) {
            listener.onError(HouseError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        ListenerRegistration registration = houseRef(db, houseId).collection(Schema.Members.COLLECTION)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        listener.onError(classify(error));
                        return;
                    }
                    List<Member> members = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Map<String, Object> data = document.getData();
                        Member member = data == null ? null : HouseDocuments.toMember(document.getId(), data, ownerId);
                        if (member != null) {
                            members.add(member);
                        }
                    }
                    members.sort((a, b) -> a.owner != b.owner
                            ? (a.owner ? -1 : 1)
                            : a.displayName.compareTo(b.displayName));
                    listener.onMembers(Collections.unmodifiableList(members), snapshot.getMetadata().isFromCache());
                });
        return registration::remove;
    }

    /** The house's open invite read from the server, or null when none is live. */
    @NonNull
    public Task<Invite> currentInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        FirebaseFirestore firestore = db;
        Task<Invite> task = houseRef(firestore, houseId).get(Source.SERVER).onSuccessTask(houseSnapshot -> {
            House house = readHouse(houseSnapshot);
            if (house == null || house.activeInviteCode == null) {
                return Tasks.forResult(null);
            }
            return inviteRef(firestore, house.activeInviteCode).get(Source.SERVER).continueWith(done -> {
                Invite invite = done.isSuccessful() ? readInvite(done.getResult()) : null;
                return invite != null && invite.houseId.equals(houseId) && invite.isLive(clock.instant())
                        ? invite : null;
            });
        });
        return translate(task);
    }

    // ---- Changes (owner) ----

    /**
     * Issues a new code and makes it the house's active one; the previous code stops working.
     * Owner only. Resolves to the invite as stored on the server.
     */
    @NonNull
    public Task<Invite> issueInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < CODE_ATTEMPTS; i++) {
            candidates.add(InviteCodes.generate(random));
        }
        Instant now = clock.instant();
        Task<String> issued = firestore.runTransaction(tx -> {
            House current = requireHouse(tx.get(house));
            if (!current.isOwner(uid)) {
                throw new HouseException.Rejection(HouseError.NOT_OWNER);
            }
            String code = null;
            for (String candidate : candidates) {
                Invite existing = readInvite(tx.get(inviteRef(firestore, candidate)));
                if (existing == null || !existing.isLive(now)) {
                    code = candidate;
                    break;
                }
            }
            if (code == null) {
                throw new HouseException.Rejection(HouseError.UNKNOWN);
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
        });
        Task<Invite> task = issued.onSuccessTask(code -> inviteRef(firestore, code).get(Source.SERVER)
                .continueWith(done -> {
                    Invite invite = done.isSuccessful() ? readInvite(done.getResult()) : null;
                    if (invite == null) {
                        throw new HouseException(HouseError.UNKNOWN);
                    }
                    return invite;
                }));
        return translate(task);
    }

    /** Closes the open invite. Owner only. */
    @NonNull
    public Task<Void> stopInvite(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        return translate(firestore.runTransaction(tx -> {
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
        }));
    }

    /** Removes another member (F-14 내보내기). They recover into a new house at their next sign-in. */
    @NonNull
    public Task<Void> removeMember(String houseId, String memberUid) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        DocumentReference member = memberRef(firestore, houseId, memberUid);
        return translate(firestore.runTransaction(tx -> {
            House current = requireHouse(tx.get(house));
            if (!current.isOwner(uid)) {
                throw new HouseException.Rejection(HouseError.NOT_OWNER);
            }
            if (!HouseRules.canRemove(current, uid, memberUid)) {
                throw new HouseException.Rejection(HouseError.INVALID_INPUT);
            }
            if (!tx.get(member).exists()) {
                throw new HouseException.Rejection(HouseError.NOT_FOUND);
            }
            tx.delete(member);
            tx.update(house, Schema.Houses.MEMBER_COUNT, FieldValue.increment(-1));
            return null;
        }));
    }

    /** Sets the forecast region (F-08). Owner only. */
    @NonNull
    public Task<Void> setRegion(String houseId, String regionName, int nx, int ny) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        if (!HouseRules.isValidRegion(regionName, nx, ny)) {
            return fail(HouseError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        return translate(firestore.runTransaction(tx -> {
            House current = requireHouse(tx.get(house));
            if (!current.isOwner(uid)) {
                throw new HouseException.Rejection(HouseError.NOT_OWNER);
            }
            tx.update(house, HouseDocuments.regionFields(regionName, nx, ny));
            return null;
        }));
    }

    // ---- Changes (any member) ----

    /**
     * Joins the house of a live invite. The caller's current house must be their own empty
     * one-person house; it is deleted in the same transaction. Resolves to the joined house id.
     * A server refusal (house full, code replaced meanwhile) is {@link HouseError#CODE_REJECTED}.
     */
    @NonNull
    public Task<String> join(String code) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        if (!InviteCodes.isValid(code)) {
            return fail(HouseError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference user = userRef(firestore, uid);
        Instant now = clock.instant();
        Task<String> task = firestore.runTransaction(tx -> {
            Invite invite = readInvite(tx.get(inviteRef(firestore, code)));
            if (invite == null || !invite.isLive(now)) {
                throw new HouseException.Rejection(HouseError.INVALID_CODE);
            }
            DocumentSnapshot userSnapshot = tx.get(user);
            String oldHouseId = userSnapshot.getString(Schema.Users.CURRENT_HOUSE_ID);
            String name = userSnapshot.getString(Schema.Users.DISPLAY_NAME);
            if (oldHouseId == null || name == null) {
                throw new HouseException.Rejection(HouseError.NOT_FOUND);
            }
            if (oldHouseId.equals(invite.houseId)) {
                throw new HouseException.Rejection(HouseError.ALREADY_MEMBER);
            }
            House oldHouse = requireHouse(tx.get(houseRef(firestore, oldHouseId)));
            switch (HouseRules.canJoinFrom(oldHouse, uid)) {
                case NOT_OWNER_OF_CURRENT:
                    throw new HouseException.Rejection(HouseError.LEAVE_FIRST);
                case CURRENT_HOUSE_NOT_EMPTY:
                    throw new HouseException.Rejection(HouseError.HOUSE_NOT_EMPTY);
                case NONE:
                default:
                    break;
            }
            Invite oldInvite = oldHouse.activeInviteCode == null
                    ? null : readInvite(tx.get(inviteRef(firestore, oldHouse.activeInviteCode)));

            tx.set(memberRef(firestore, invite.houseId, uid),
                    HouseDocuments.joinedMemberFields(name, code, FieldValue.serverTimestamp()));
            tx.update(houseRef(firestore, invite.houseId), Schema.Houses.MEMBER_COUNT, FieldValue.increment(1));
            tx.update(user, HouseholdSetup.moveUser(invite.houseId, FieldValue.serverTimestamp()));
            tx.delete(memberRef(firestore, oldHouseId, uid));
            tx.delete(houseRef(firestore, oldHouseId));
            if (oldInvite != null && oldInvite.houseId.equals(oldHouseId)) {
                tx.delete(inviteRef(firestore, oldHouse.activeInviteCode));
            }
            return invite.houseId;
        });
        return translateJoin(task);
    }

    /**
     * Leaves a house the caller does not own and moves them to a new one-person house, in one
     * transaction. Resolves to the new house id.
     */
    @NonNull
    public Task<String> leave(String houseId) {
        if (db == null) {
            return fail(HouseError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(HouseError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference house = houseRef(firestore, houseId);
        DocumentReference user = userRef(firestore, uid);
        DocumentReference newHouse = firestore.collection(Schema.Houses.COLLECTION).document();
        return translate(firestore.runTransaction(tx -> {
            House current = requireHouse(tx.get(house));
            if (!HouseRules.canLeave(current, uid)) {
                throw new HouseException.Rejection(HouseError.OWNER_CANNOT_LEAVE);
            }
            String name = tx.get(user).getString(Schema.Users.DISPLAY_NAME);
            if (name == null) {
                throw new HouseException.Rejection(HouseError.NOT_FOUND);
            }
            tx.delete(memberRef(firestore, houseId, uid));
            tx.update(house, Schema.Houses.MEMBER_COUNT, FieldValue.increment(-1));
            tx.set(newHouse, HouseholdSetup.newHouse(uid, FieldValue.serverTimestamp()));
            tx.set(memberRef(firestore, newHouse.getId(), uid), HouseholdSetup.newMember(name, FieldValue.serverTimestamp()));
            tx.update(user, HouseholdSetup.moveUser(newHouse.getId(), FieldValue.serverTimestamp()));
            return newHouse.getId();
        }));
    }

    // ---- Helpers ----

    private static DocumentReference houseRef(FirebaseFirestore firestore, String houseId) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId);
    }

    private static DocumentReference memberRef(FirebaseFirestore firestore, String houseId, String uid) {
        return houseRef(firestore, houseId).collection(Schema.Members.COLLECTION).document(uid);
    }

    private static DocumentReference userRef(FirebaseFirestore firestore, String uid) {
        return firestore.collection(Schema.Users.COLLECTION).document(uid);
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

    /** Null when missing or malformed; the server timestamp becomes an {@link Instant}. */
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
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private static <T> Task<T> translate(Task<T> task) {
        return task.continueWithTask(done -> done.isSuccessful()
                ? done
                : Tasks.forException(asHouseException(done.getException())));
    }

    /** Joining: a rules refusal means the house filled up or the code changed after the checks. */
    private static <T> Task<T> translateJoin(Task<T> task) {
        return task.continueWithTask(done -> {
            if (done.isSuccessful()) {
                return done;
            }
            HouseException failure = asHouseException(done.getException());
            return Tasks.forException(failure.error == HouseError.PERMISSION_DENIED
                    ? new HouseException(HouseError.CODE_REJECTED, failure)
                    : failure);
        });
    }

    private static HouseException asHouseException(Exception e) {
        return e instanceof HouseException ? (HouseException) e : new HouseException(classify(e), e);
    }

    static HouseError classify(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HouseException) {
                return ((HouseException) t).error;
            }
            if (t instanceof HouseException.Rejection) {
                return ((HouseException.Rejection) t).error;
            }
            if (t instanceof FirebaseNetworkException) {
                return HouseError.NETWORK;
            }
            if (t instanceof FirebaseFirestoreException) {
                return HouseError.fromFirestoreCode(((FirebaseFirestoreException) t).getCode().name());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return HouseError.UNKNOWN;
    }

    private static <T> Task<T> fail(HouseError error) {
        return Tasks.forException(new HouseException(error));
    }
}
