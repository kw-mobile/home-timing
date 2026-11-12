package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.ChoreDraft;
import com.kwmobile.hometiming.domain.ChoreInputValidator;
import com.kwmobile.hometiming.domain.ChoreLog;
import com.kwmobile.hometiming.domain.ChoreSchedule;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.ItemSchedule;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Weekly chores of one house (docs/MVP.md F-07·F-09, docs/DATA_MODEL.md chores/logs).
 *
 * <p>Like {@link ItemRepository}: reads are live listeners that also serve the cache, and every
 * change is a transaction, so offline changes fail with {@link ItemError#NETWORK}. Errors reuse
 * {@link ItemError}. Changes take the occurrence and revision the screen or suggestion was based on,
 * so a stale card cannot overwrite a newer schedule.
 */
public final class ChoreRepository {
    public interface ChoresListener {
        void onChores(@NonNull List<Chore> chores, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    public interface ChoreListener {
        /** {@code chore} is null when it was deleted. */
        void onChore(@Nullable Chore chore, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    public interface LogsListener {
        /** Newest first. */
        void onLogs(@NonNull List<ChoreLog> logs, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    private static volatile ChoreRepository instance;

    @Nullable
    private final FirebaseFirestore db;
    @Nullable
    private final FirebaseAuth auth;
    private final Clock clock;

    private ChoreRepository(@Nullable FirebaseFirestore db, @Nullable FirebaseAuth auth, Clock clock) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
    }

    /** Without app/google-services.json every call reports {@link ItemError#NOT_CONFIGURED}. */
    @NonNull
    public static ChoreRepository getInstance() {
        ChoreRepository local = instance;
        if (local == null) {
            synchronized (ChoreRepository.class) {
                local = instance;
                if (local == null) {
                    local = BuildConfig.FIREBASE_CONFIGURED
                            ? new ChoreRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance(),
                                    Clock.systemUTC())
                            : new ChoreRepository(null, null, Clock.systemUTC());
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---- Reads ----

    /** All chores of the house, earliest first (single-field index). */
    @NonNull
    public Subscription listenChores(String houseId, ChoresListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(currentUid(), houseId);
        if (scope == null) {
            listener.onError(ItemError.NOT_SIGNED_IN);
            return Subscription.NONE;
        }
        Query query = chores(db, houseId).orderBy(Schema.Chores.NEXT_DUE_DATE);
        ListenerRegistration registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (!OperationScope.getInstance().isCurrent(scope, currentUid())) return;
            if (error != null || snapshot == null) {
                listener.onError(ItemTasks.classify(error));
                return;
            }
            List<Chore> list = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Chore chore = read(document);
                if (chore != null) {
                    list.add(chore);
                }
            }
            listener.onChores(Collections.unmodifiableList(list), snapshot.getMetadata().isFromCache());
        });
        return ItemTasks.subscription(scope, registration);
    }

    @NonNull
    public Subscription listenChore(String houseId, String choreId, ChoreListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(currentUid(), houseId);
        if (scope == null) {
            listener.onError(ItemError.NOT_SIGNED_IN);
            return Subscription.NONE;
        }
        ListenerRegistration registration = choreRef(db, houseId, choreId)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (!OperationScope.getInstance().isCurrent(scope, currentUid())) return;
            if (error != null || snapshot == null) {
                listener.onError(ItemTasks.classify(error));
                return;
            }
            listener.onChore(snapshot.exists() ? read(snapshot) : null, snapshot.getMetadata().isFromCache());
        });
        return ItemTasks.subscription(scope, registration);
    }

    @NonNull
    public Subscription listenLogs(String houseId, String choreId, LogsListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(currentUid(), houseId);
        if (scope == null) {
            listener.onError(ItemError.NOT_SIGNED_IN);
            return Subscription.NONE;
        }
        Query query = choreRef(db, houseId, choreId).collection(Schema.Logs.COLLECTION)
                .orderBy(Schema.Logs.CREATED_AT, Query.Direction.DESCENDING);
        ListenerRegistration registration = query.addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (!OperationScope.getInstance().isCurrent(scope, currentUid())) return;
            if (error != null || snapshot == null) {
                listener.onError(ItemTasks.classify(error));
                return;
            }
            List<ChoreLog> list = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Map<String, Object> data = document.getData();
                ChoreLog log = data == null ? null : ChoreDocuments.toLog(data);
                if (log != null) {
                    list.add(log);
                }
            }
            listener.onLogs(Collections.unmodifiableList(list), snapshot.getMetadata().isFromCache());
        });
        return ItemTasks.subscription(scope, registration);
    }

    // ---- Changes (online only) ----

    /** Registers a chore and raises the house choreCount in the same commit. */
    @NonNull
    public Task<Chore> create(String houseId, ChoreDraft draft) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ChoreInputValidator.validateDraft(draft, today()).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = chores(firestore, houseId).document();
        Chore chore = ChoreSchedule.register(ref.getId(), draft, uid, newOccurrenceId());
        return ItemTasks.commit(firestore, scope, this::currentUid, tx -> {
            tx.set(ref, ChoreDocuments.choreFields(chore, uid, FieldValue.serverTimestamp(),
                    FieldValue.serverTimestamp()));
            tx.update(houseRef(firestore, houseId), Schema.Houses.CHORE_COUNT, FieldValue.increment(1),
                    Schema.Houses.CHORE_MUTATION_ID, ref.getId());
            return chore;
        });
    }

    /** Edits the chore the screen showed. A new weekday restarts this occurrence on that weekday. */
    @NonNull
    public Task<Chore> update(String houseId, Chore shown, ChoreDraft draft) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        LocalDate today = today();
        if (!ChoreInputValidator.validateDraft(draft, today).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = choreRef(firestore, houseId, shown.id);
        return ItemTasks.commitWithConflictCheck(firestore, scope, this::currentUid, ref,
                snapshot -> conflictOf(snapshot, shown.occurrenceId, shown.revision), tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            Chore stored = requireStored(snapshot);
            rejectConflict(stored, shown.occurrenceId, shown.revision);
            Chore next = ChoreSchedule.edit(stored, draft, today);
            Map<String, Object> fields = ChoreDocuments.choreFields(next, uid, createdAt(snapshot),
                    FieldValue.serverTimestamp());
            tx.set(ref, RecordState.preserve(fields, snapshot, null));
            return next;
        });
    }

    /**
     * Completes the occurrence the screen showed: writes its log and starts the next one on the
     * first original weekday after the done date, in one transaction.
     */
    @NonNull
    public Task<Chore> complete(String houseId, Chore shown, LocalDate doneDate) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ChoreInputValidator.validateDoneDate(doneDate, today()).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = choreRef(firestore, houseId, shown.id);
        DocumentReference member = memberRef(firestore, houseId, uid);
        String nextOccurrence = newOccurrenceId();
        return ItemTasks.commitWithConflictCheck(firestore, scope, this::currentUid, ref,
                snapshot -> conflictOf(snapshot, shown.occurrenceId, shown.revision), tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            String name = tx.get(member).getString(Schema.Members.DISPLAY_NAME);
            Chore stored = requireStored(snapshot);
            rejectConflict(stored, shown.occurrenceId, shown.revision);
            if (name == null) {
                throw new ItemException.Rejection(ItemError.PERMISSION_DENIED);
            }
            Chore next = ChoreSchedule.complete(stored, doneDate, nextOccurrence);
            tx.set(ref.collection(Schema.Logs.COLLECTION).document(ChoreDocuments.doneLogId(stored.occurrenceId)),
                    ChoreDocuments.doneLogFields(stored.occurrenceId, doneDate, uid, name,
                            FieldValue.serverTimestamp()));
            Map<String, Object> fields = ChoreDocuments.choreFields(next, uid, createdAt(snapshot),
                    FieldValue.serverTimestamp());
            tx.set(ref, RecordState.preserve(fields, snapshot, ChoreDocuments.doneLogId(stored.occurrenceId)));
            return next;
        });
    }

    /**
     * Moves only this occurrence (a weather suggestion or a manual change). Pass the occurrence and
     * revision the card or screen was based on; {@code reason} is {@link Schema.Logs#REASON_RAIN_FORECAST}
     * or {@link Schema.Logs#REASON_USER}.
     */
    @NonNull
    public Task<Chore> reschedule(String houseId, String choreId, String occurrenceId, int revision,
                                  LocalDate target, String reason) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ChoreSchedule.canReschedule(target, today())) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = choreRef(firestore, houseId, choreId);
        DocumentReference member = memberRef(firestore, houseId, uid);
        return ItemTasks.commitWithConflictCheck(firestore, scope, this::currentUid, ref,
                snapshot -> conflictOf(snapshot, occurrenceId, revision), tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            String name = tx.get(member).getString(Schema.Members.DISPLAY_NAME);
            Chore stored = requireStored(snapshot);
            rejectConflict(stored, occurrenceId, revision);
            if (name == null) {
                throw new ItemException.Rejection(ItemError.PERMISSION_DENIED);
            }
            Chore next = ChoreSchedule.reschedule(stored, target);
            if (next == stored) {
                return stored;
            }
            tx.set(ref.collection(Schema.Logs.COLLECTION)
                            .document(ChoreDocuments.movedLogId(next.occurrenceId, next.revision)),
                    ChoreDocuments.movedLogFields(next.occurrenceId, stored.nextDueDate, target, reason, uid, name,
                            FieldValue.serverTimestamp()));
            Map<String, Object> fields = ChoreDocuments.choreFields(next, uid, createdAt(snapshot),
                    FieldValue.serverTimestamp());
            tx.set(ref, RecordState.preserve(fields, snapshot,
                    ChoreDocuments.movedLogId(next.occurrenceId, next.revision)));
            return next;
        });
    }

    /** "그대로 두기": hides the suggestion for this occurrence only. */
    @NonNull
    public Task<Chore> dismissSuggestion(String houseId, String choreId, String occurrenceId, int revision) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = choreRef(firestore, houseId, choreId);
        return ItemTasks.commitWithConflictCheck(firestore, scope, this::currentUid, ref,
                snapshot -> conflictOf(snapshot, occurrenceId, revision), tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            Chore stored = requireStored(snapshot);
            rejectConflict(stored, occurrenceId, revision);
            Chore next = ChoreSchedule.dismissSuggestion(stored);
            if (next != stored) {
                Map<String, Object> fields = ChoreDocuments.choreFields(next, uid, createdAt(snapshot),
                        FieldValue.serverTimestamp());
                tx.set(ref, RecordState.preserve(fields, snapshot, null));
            }
            return next;
        });
    }

    /**
     * Deletes a chore with its logs and lowers the house choreCount. Registrant or owner only. Logs
     * are removed after freezing the parent, before the parent and its house count are removed.
     */
    @NonNull
    public Task<Void> delete(String houseId, String choreId) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        OperationScope.Token scope = OperationScope.getInstance().capture(uid, houseId);
        if (scope == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        DocumentReference ref = choreRef(db, houseId, choreId);
        return ItemTasks.scoped(DocumentDeletion.delete(db, ref, Schema.Logs.COLLECTION,
                Schema.Houses.CHORE_COUNT, Schema.Houses.CHORE_MUTATION_ID, uid, this::currentUid, scope), scope, this::currentUid);
    }

    // ---- Helpers ----

    private static CollectionReference chores(FirebaseFirestore firestore, String houseId) {
        return houseRef(firestore, houseId).collection(Schema.Chores.COLLECTION);
    }

    private static DocumentReference houseRef(FirebaseFirestore firestore, String houseId) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId);
    }

    private static DocumentReference choreRef(FirebaseFirestore firestore, String houseId, String choreId) {
        return chores(firestore, houseId).document(choreId);
    }

    private static DocumentReference memberRef(FirebaseFirestore firestore, String houseId, String uid) {
        return houseRef(firestore, houseId).collection(Schema.Members.COLLECTION).document(uid);
    }

    @Nullable
    private static Chore read(DocumentSnapshot document) {
        Map<String, Object> data = document.getData();
        return data == null ? null : ChoreDocuments.toChore(document.getId(), data);
    }

    private static Chore requireStored(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            throw new ItemException.Rejection(ItemError.NOT_FOUND);
        }
        RecordState.requireActive(snapshot);
        Chore stored = read(snapshot);
        if (stored == null) {
            throw new ItemException.Rejection(ItemError.UNKNOWN);
        }
        return stored;
    }

    @Nullable
    private static ItemSchedule.Conflict conflictOf(DocumentSnapshot snapshot, String occurrenceId, int revision) {
        Chore stored = read(snapshot);
        return stored == null ? null : ChoreSchedule.conflict(stored, occurrenceId, revision);
    }

    private static void rejectConflict(Chore stored, String occurrenceId, int revision) {
        ItemSchedule.Conflict conflict = ChoreSchedule.conflict(stored, occurrenceId, revision);
        if (conflict == ItemSchedule.Conflict.ALREADY_COMPLETED) {
            throw new ItemException.Rejection(ItemError.ALREADY_COMPLETED);
        }
        if (conflict == ItemSchedule.Conflict.SCHEDULE_CHANGED) {
            throw new ItemException.Rejection(ItemError.SCHEDULE_CHANGED);
        }
    }

    /** The stored creation time, written back unchanged so the rules see createdAt as untouched. */
    private static Object createdAt(DocumentSnapshot snapshot) {
        Object createdAt = snapshot.get(Schema.Chores.CREATED_AT);
        if (createdAt == null) {
            throw new ItemException.Rejection(ItemError.UNKNOWN);
        }
        return createdAt;
    }

    @Nullable
    private String currentUid() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    private LocalDate today() {
        return DateRules.today(clock);
    }

    private static String newOccurrenceId() {
        return UUID.randomUUID().toString();
    }

    private static <T> Task<T> fail(ItemError error) {
        return Tasks.forException(new ItemException(error));
    }
}
