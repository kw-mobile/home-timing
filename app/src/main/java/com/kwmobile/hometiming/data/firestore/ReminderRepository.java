package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.Item;
import com.kwmobile.hometiming.domain.ReminderChange;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.domain.ReminderPlanner;
import com.kwmobile.hometiming.domain.ReminderRules;
import com.kwmobile.hometiming.domain.ScheduleState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The data the local reminders need (docs/MVP.md 날씨·알림 계약). Scheduling, receivers and workers
 * live in {@code notify}; this class only answers from the server and reports changes.
 */
public final class ReminderRepository {
    public interface ChangesListener {
        void onChanges(@NonNull List<ReminderChange> changes);

        void onError(@NonNull ItemError error);
    }

    /** Short enough to finish inside a broadcast receiver's time limit. */
    static final long CHECK_TIMEOUT_SECONDS = 5;

    private static volatile ReminderRepository instance;

    @Nullable
    private final FirebaseFirestore db;
    @Nullable
    private final FirebaseAuth auth;

    private ReminderRepository(@Nullable FirebaseFirestore db, @Nullable FirebaseAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    @NonNull
    public static ReminderRepository getInstance() {
        ReminderRepository local = instance;
        if (local == null) {
            synchronized (ReminderRepository.class) {
                local = instance;
                if (local == null) {
                    local = BuildConfig.FIREBASE_CONFIGURED
                            ? new ReminderRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
                            : new ReminderRepository(null, null);
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Asks the server, within {@link #CHECK_TIMEOUT_SECONDS}, whether a reminder should fire. The
     * task always succeeds: when the server cannot be asked the answer is
     * {@link ReminderDecision#UNCONFIRMED}, and the reminder fires from the stored schedule.
     *
     * @param revision the revision the reminder was scheduled from
     */
    @NonNull
    public Task<ReminderDecision> checkBeforeFire(ReminderKey key, int revision) {
        if (db == null) {
            return Tasks.forResult(ReminderDecision.UNCONFIRMED);
        }
        String uid = currentUid();
        if (uid == null) {
            return Tasks.forResult(decisionForFailure(ItemError.NOT_SIGNED_IN));
        }
        String collection = key.kind == ReminderKey.Kind.ITEM ? Schema.Items.COLLECTION : Schema.Chores.COLLECTION;
        DocumentReference ref = db.collection(Schema.Houses.COLLECTION).document(key.houseId)
                .collection(collection).document(key.documentId);
        DocumentReference receipt = db.collection(Schema.AccountDeletions.COLLECTION).document(uid);
        DocumentReference home = db.collection(Schema.Houses.COLLECTION).document(key.houseId);
        Task<ReminderDecision> check = receipt.get(Source.SERVER).onSuccessTask(marker -> {
            if (marker.exists()) return Tasks.forResult(ReminderDecision.CANCEL);
            return home.get(Source.SERVER).onSuccessTask(house -> {
                if (!house.exists() || Boolean.TRUE.equals(house.getBoolean(Schema.Houses.CLOSING))) {
                    return Tasks.forResult(ReminderDecision.CANCEL);
                }
                return ref.get(Source.SERVER).continueWith(done -> done.isSuccessful()
                        ? decide(key, revision, done.getResult())
                        : decisionForFailure(ItemTasks.classify(done.getException())));
            });
        });
        return Tasks.withTimeout(check, CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .continueWith(done -> !uid.equals(currentUid()) ? ReminderDecision.CANCEL
                        : done.isSuccessful() ? done.getResult()
                        : decisionForFailure(ItemTasks.classify(done.getException())));
    }

    private static ReminderDecision decide(ReminderKey key, int revision, DocumentSnapshot snapshot) {
        if (!snapshot.exists() || Boolean.TRUE.equals(snapshot.getBoolean("deleting"))) {
            return ReminderRules.check(null, key.occurrenceId, revision);
        }
        ScheduleState state = stateOf(key.kind, snapshot);
        return state == null ? ReminderDecision.UNCONFIRMED : ReminderRules.check(state, key.occurrenceId, revision);
    }

    /** Refused means this user no longer belongs there; anything else just could not be checked. */
    static ReminderDecision decisionForFailure(ItemError error) {
        return error == ItemError.PERMISSION_DENIED || error == ItemError.NOT_SIGNED_IN
                ? ReminderDecision.CANCEL
                : ReminderDecision.UNCONFIRMED;
    }

    @Nullable
    private static ScheduleState stateOf(ReminderKey.Kind kind, DocumentSnapshot snapshot) {
        Map<String, Object> data = snapshot.getData();
        if (data == null) {
            return null;
        }
        if (kind == ReminderKey.Kind.ITEM) {
            Item item = ItemDocuments.toItem(snapshot.getId(), data);
            return item == null ? null : ScheduleState.of(item);
        }
        Chore chore = ChoreDocuments.toChore(snapshot.getId(), data);
        return chore == null ? null : ScheduleState.of(chore);
    }

    /**
     * The user's settings and all reminder targets, read from the server for background sync. Fails
     * when the server cannot be reached, because re-reading the cache is not a sync.
     */
    @NonNull
    public Task<ReminderSnapshot> readFromServer() {
        return readFromServer(currentUid(), null);
    }

    /** A worker pins the identity and house it started with; a session change cannot adopt its result. */
    @NonNull
    public Task<ReminderSnapshot> readFromServer(@Nullable String expectedUid, @Nullable String expectedHouseId) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        if (expectedUid == null || !expectedUid.equals(currentUid())) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference userRef = firestore.collection(Schema.Users.COLLECTION).document(expectedUid);
        Task<ReminderSnapshot> task = firestore.collection(Schema.AccountDeletions.COLLECTION).document(expectedUid)
                .get(Source.SERVER).onSuccessTask(marker -> marker.exists()
                        ? ReminderRepository.<DocumentSnapshot>fail(ItemError.PERMISSION_DENIED) : userRef.get(Source.SERVER)).onSuccessTask(user -> {
            if (!expectedUid.equals(currentUid())) {
                return fail(ItemError.NOT_SIGNED_IN);
            }
            String houseId = user.getString(Schema.Users.CURRENT_HOUSE_ID);
            String notifyTime = user.getString(Schema.Users.NOTIFY_TIME);
            Long advanceDays = user.getLong(Schema.Users.ADVANCE_DAYS);
            if (houseId == null || notifyTime == null || advanceDays == null) {
                return fail(ItemError.NOT_FOUND);
            }
            if (expectedHouseId != null && !expectedHouseId.equals(houseId)) {
                return fail(ItemError.PERMISSION_DENIED);
            }
            DocumentReference house = firestore.collection(Schema.Houses.COLLECTION).document(houseId);
            return house.get(Source.SERVER).onSuccessTask(home -> {
                if (!home.exists() || Boolean.TRUE.equals(home.getBoolean(Schema.Houses.CLOSING))) {
                    return ReminderRepository.<QuerySnapshot>fail(ItemError.PERMISSION_DENIED);
                }
                return house.collection(Schema.Items.COLLECTION).get(Source.SERVER);
            }).onSuccessTask(items -> house.collection(Schema.Chores.COLLECTION).get(Source.SERVER)
                            .onSuccessTask(chores -> userRef.get(Source.SERVER).onSuccessTask(latest -> {
                                if (!expectedUid.equals(currentUid())) {
                                    return fail(ItemError.NOT_SIGNED_IN);
                                }
                                if (!houseId.equals(latest.getString(Schema.Users.CURRENT_HOUSE_ID))) {
                                    return fail(ItemError.PERMISSION_DENIED);
                                }
                                if (!notifyTime.equals(latest.getString(Schema.Users.NOTIFY_TIME))
                                        || !advanceDays.equals(latest.getLong(Schema.Users.ADVANCE_DAYS))) {
                                    return fail(ItemError.SCHEDULE_CHANGED);
                                }
                                return house.collection(Schema.Members.COLLECTION).document(expectedUid)
                                        .get(Source.SERVER).onSuccessTask(member -> {
                                            if (!expectedUid.equals(currentUid())) {
                                                return fail(ItemError.NOT_SIGNED_IN);
                                            }
                                            return member.exists() ? Tasks.forResult(new ReminderSnapshot(houseId,
                                                    notifyTime, advanceDays.intValue(), readItems(items), readChores(chores)))
                                                    : fail(ItemError.PERMISSION_DENIED);
                                        });
                            })));
        });
        return task.continueWithTask(done -> done.isSuccessful()
                ? done : Tasks.forException(asItemException(done.getException())));
    }

    /** Saves the personal reminder settings. Online only, like every other change. */
    @NonNull
    public Task<Void> updateSettings(String notifyTime, int advanceDays) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ReminderPlanner.isValidSettings(notifyTime, advanceDays)) {
            return fail(ItemError.INVALID_INPUT);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid);
        if (scope == null) return fail(ItemError.NOT_SIGNED_IN);
        FirebaseFirestore firestore = db;
        DocumentReference user = firestore.collection(Schema.Users.COLLECTION).document(uid);
        Task<Void> task = ItemTasks.transaction(firestore, scope, this::currentUid, tx -> {
            if (!uid.equals(currentUid())) {
                throw new ItemException.Rejection(ItemError.NOT_SIGNED_IN);
            }
            if (!scope.houseId.equals(tx.get(user).getString(Schema.Users.CURRENT_HOUSE_ID))) {
                throw new ItemException.Rejection(ItemError.NOT_SIGNED_IN);
            }
            tx.update(user, Schema.Users.NOTIFY_TIME, notifyTime, Schema.Users.ADVANCE_DAYS, advanceDays,
                    Schema.Users.UPDATED_AT, FieldValue.serverTimestamp());
            return null;
        });
        return ItemTasks.scoped(task, scope, this::currentUid);
    }

    private static List<Item> readItems(QuerySnapshot snapshot) {
        List<Item> items = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> data = document.getData();
            Item item = data == null || Boolean.TRUE.equals(data.get("deleting"))
                    ? null : ItemDocuments.toItem(document.getId(), data);
            if (item != null) {
                items.add(item);
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static List<Chore> readChores(QuerySnapshot snapshot) {
        List<Chore> chores = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> data = document.getData();
            Chore chore = data == null || Boolean.TRUE.equals(data.get("deleting"))
                    ? null : ChoreDocuments.toChore(document.getId(), data);
            if (chore != null) {
                chores.add(chore);
            }
        }
        return Collections.unmodifiableList(chores);
    }

    /**
     * Reports completions, schedule changes, additions and deletions in a house while the app runs,
     * including those from other devices. Remove it when the house changes or the user signs out.
     */
    @NonNull
    public Subscription listenChanges(String houseId, ChangesListener listener) {
        String uid = currentUid();
        if (uid == null) {
            listener.onError(ItemError.NOT_SIGNED_IN);
            return Subscription.NONE;
        }
        ReminderRules.Tracker itemTracker = new ReminderRules.Tracker();
        ReminderRules.Tracker choreTracker = new ReminderRules.Tracker();
        Subscription items = ItemRepository.getInstance().listenItems(houseId, new ItemRepository.ItemsListener() {
            @Override
            public void onItems(@NonNull List<Item> list, boolean fromCache) {
                if (!uid.equals(currentUid())) {
                    return;
                }
                List<ScheduleState> states = new ArrayList<>(list.size());
                for (Item item : list) {
                    states.add(ScheduleState.of(item));
                }
                report(itemTracker.update(states), listener);
            }

            @Override
            public void onError(@NonNull ItemError error) {
                listener.onError(error);
            }
        });
        Subscription chores = ChoreRepository.getInstance().listenChores(houseId, new ChoreRepository.ChoresListener() {
            @Override
            public void onChores(@NonNull List<Chore> list, boolean fromCache) {
                if (!uid.equals(currentUid())) {
                    return;
                }
                List<ScheduleState> states = new ArrayList<>(list.size());
                for (Chore chore : list) {
                    states.add(ScheduleState.of(chore));
                }
                report(choreTracker.update(states), listener);
            }

            @Override
            public void onError(@NonNull ItemError error) {
                listener.onError(error);
            }
        });
        return () -> {
            items.remove();
            chores.remove();
        };
    }

    private static void report(List<ReminderChange> changes, ChangesListener listener) {
        if (!changes.isEmpty()) {
            listener.onChanges(changes);
        }
    }

    @Nullable
    public String currentUid() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private static ItemException asItemException(Exception e) {
        return e instanceof ItemException ? (ItemException) e : new ItemException(ItemTasks.classify(e), e);
    }

    private static <T> Task<T> fail(ItemError error) {
        return Tasks.forException(new ItemException(error));
    }
}
