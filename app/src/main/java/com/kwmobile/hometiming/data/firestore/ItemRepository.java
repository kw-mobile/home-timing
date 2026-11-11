package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.HistoryEntry;
import com.kwmobile.hometiming.domain.Item;
import com.kwmobile.hometiming.domain.ItemDraft;
import com.kwmobile.hometiming.domain.ItemInputValidator;
import com.kwmobile.hometiming.domain.ItemSchedule;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Replacement items of one house (docs/MVP.md F-02~F-05, docs/DATA_MODEL.md items/history).
 *
 * <p>Reads are live listeners that also serve the offline cache ({@code fromCache} tells the screen
 * to keep changes disabled). Every change commits in a transaction: transactions need the server, so
 * an offline change fails with {@link ItemError#NETWORK} instead of showing as saved and waiting in a
 * local queue. Screens pass the {@link Item} they showed so concurrent completion and edits are told
 * apart ({@link ItemError#ALREADY_COMPLETED} vs {@link ItemError#SCHEDULE_CHANGED}).
 */
public final class ItemRepository {
    public interface ItemsListener {
        void onItems(@NonNull List<Item> items, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    public interface ItemListener {
        /** {@code item} is null when the item was deleted. */
        void onItem(@Nullable Item item, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    public interface HistoryListener {
        /** Newest first. */
        void onHistory(@NonNull List<HistoryEntry> history, boolean fromCache);

        void onError(@NonNull ItemError error);
    }

    /** History documents removed per transaction while deleting an item (limit 500 writes). */
    static final int HISTORY_DELETE_CHUNK = 400;

    private static volatile ItemRepository instance;

    @Nullable
    private final FirebaseFirestore db;
    @Nullable
    private final FirebaseAuth auth;
    private final Clock clock;

    private ItemRepository(@Nullable FirebaseFirestore db, @Nullable FirebaseAuth auth, Clock clock) {
        this.db = db;
        this.auth = auth;
        this.clock = clock;
    }

    /** Without app/google-services.json every call reports {@link ItemError#NOT_CONFIGURED}. */
    @NonNull
    public static ItemRepository getInstance() {
        ItemRepository local = instance;
        if (local == null) {
            synchronized (ItemRepository.class) {
                local = instance;
                if (local == null) {
                    local = BuildConfig.FIREBASE_CONFIGURED
                            ? new ItemRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance(),
                                    Clock.systemUTC())
                            : new ItemRepository(null, null, Clock.systemUTC());
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---- Reads ----

    /** All items of the house, earliest due first (single-field index, no composite index). */
    @NonNull
    public Subscription listenItems(String houseId, ItemsListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        Query query = items(db, houseId).orderBy(Schema.Items.NEXT_DUE_DATE);
        ListenerRegistration registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                listener.onError(classify(error));
                return;
            }
            List<Item> list = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Item item = read(document);
                if (item != null) {
                    list.add(item);
                }
            }
            listener.onItems(Collections.unmodifiableList(list), snapshot.getMetadata().isFromCache());
        });
        return registration::remove;
    }

    @NonNull
    public Subscription listenItem(String houseId, String itemId, ItemListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        ListenerRegistration registration = itemRef(db, houseId, itemId).addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                listener.onError(classify(error));
                return;
            }
            listener.onItem(snapshot.exists() ? read(snapshot) : null, snapshot.getMetadata().isFromCache());
        });
        return registration::remove;
    }

    @NonNull
    public Subscription listenHistory(String houseId, String itemId, HistoryListener listener) {
        if (db == null) {
            listener.onError(ItemError.NOT_CONFIGURED);
            return Subscription.NONE;
        }
        Query query = itemRef(db, houseId, itemId).collection(Schema.History.COLLECTION)
                .orderBy(Schema.History.REPLACED_DATE, Query.Direction.DESCENDING);
        ListenerRegistration registration = query.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null) {
                listener.onError(classify(error));
                return;
            }
            List<HistoryEntry> list = new ArrayList<>();
            for (DocumentSnapshot document : snapshot.getDocuments()) {
                Map<String, Object> data = document.getData();
                HistoryEntry entry = data == null ? null : ItemDocuments.toHistory(document.getId(), data);
                if (entry != null) {
                    list.add(entry);
                }
            }
            listener.onHistory(Collections.unmodifiableList(list), snapshot.getMetadata().isFromCache());
        });
        return registration::remove;
    }

    // ---- Changes (online only) ----

    /** Registers an item and raises the house itemCount in the same commit. */
    @NonNull
    public Task<Item> create(String houseId, ItemDraft draft) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ItemInputValidator.validateDraft(draft, today()).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = items(firestore, houseId).document();
        Item item = ItemSchedule.register(ref.getId(), draft, uid, newOccurrenceId());
        return translate(firestore.runTransaction(tx -> {
            tx.set(ref, ItemDocuments.itemFields(item, uid, FieldValue.serverTimestamp(),
                    FieldValue.serverTimestamp()));
            tx.update(houseRef(firestore, houseId), Schema.Houses.ITEM_COUNT, FieldValue.increment(1));
            return item;
        }));
    }

    /** Edits the item the screen showed (F-03). Fails if it was completed or rescheduled meanwhile. */
    @NonNull
    public Task<Item> update(String houseId, Item shown, ItemDraft draft) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        if (!ItemInputValidator.validateDraft(draft, today()).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = itemRef(firestore, houseId, shown.id);
        return explainDenial(translate(firestore.runTransaction(tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            Item stored = requireStored(snapshot);
            rejectConflict(stored, shown);
            if (!ItemSchedule.startDateFits(stored, draft.startDate)) {
                throw new ItemException.Rejection(ItemError.INVALID_INPUT);
            }
            Item next = ItemSchedule.edit(stored, draft);
            tx.set(ref, ItemDocuments.itemFields(next, uid, createdAt(snapshot), FieldValue.serverTimestamp()));
            return next;
        })), ref, snapshot -> conflictOf(snapshot, shown.occurrenceId, shown.revision));
    }

    /**
     * Completes the occurrence the screen showed (F-05): writes its history and starts the next
     * cycle from the actual replacement date, in one transaction. The date must satisfy
     * previous replacement ≤ date ≤ today (Asia/Seoul, #20); the stored item decides "previous".
     */
    @NonNull
    public Task<Item> complete(String houseId, Item shown, LocalDate replacedDate) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        LocalDate today = today();
        if (!ItemInputValidator.validateReplacedDate(replacedDate, shown.lastReplacedDate, today).isEmpty()) {
            return fail(ItemError.INVALID_INPUT);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = itemRef(firestore, houseId, shown.id);
        DocumentReference member = houseRef(firestore, houseId).collection(Schema.Members.COLLECTION).document(uid);
        String nextOccurrence = newOccurrenceId();
        return explainDenial(translate(firestore.runTransaction(tx -> {
            DocumentSnapshot snapshot = tx.get(ref);
            DocumentSnapshot memberSnapshot = tx.get(member);
            Item stored = requireStored(snapshot);
            rejectConflict(stored, shown);
            if (!ItemInputValidator.validateReplacedDate(replacedDate, stored.lastReplacedDate, today).isEmpty()) {
                throw new ItemException.Rejection(ItemError.INVALID_INPUT);
            }
            String name = memberSnapshot.getString(Schema.Members.DISPLAY_NAME);
            if (!memberSnapshot.exists() || name == null) {
                throw new ItemException.Rejection(ItemError.PERMISSION_DENIED);
            }
            Item next = ItemSchedule.complete(stored, replacedDate, nextOccurrence);
            HistoryEntry entry = ItemSchedule.historyOf(stored, replacedDate, uid, name);
            tx.set(ref.collection(Schema.History.COLLECTION).document(stored.occurrenceId),
                    ItemDocuments.historyFields(entry, FieldValue.serverTimestamp()));
            tx.set(ref, ItemDocuments.itemFields(next, uid, createdAt(snapshot), FieldValue.serverTimestamp()));
            return next;
        })), ref, snapshot -> conflictOf(snapshot, shown.occurrenceId, shown.revision));
    }

    /**
     * Deletes an item with its history and lowers the house itemCount. Registrant or owner only.
     * History goes first in chunks; the last transaction removes the item and the count together.
     */
    @NonNull
    public Task<Void> delete(String houseId, String itemId) {
        if (db == null) {
            return fail(ItemError.NOT_CONFIGURED);
        }
        String uid = currentUid();
        if (uid == null) {
            return fail(ItemError.NOT_SIGNED_IN);
        }
        FirebaseFirestore firestore = db;
        DocumentReference ref = itemRef(firestore, houseId, itemId);
        DocumentReference house = houseRef(firestore, houseId);
        Task<Void> task = ref.get(Source.SERVER)
                .onSuccessTask(itemSnapshot -> house.get(Source.SERVER).onSuccessTask(houseSnapshot -> {
                    Item stored = itemSnapshot.exists() ? read(itemSnapshot) : null;
                    if (stored == null) {
                        return fail(ItemError.NOT_FOUND);
                    }
                    if (!stored.canDelete(uid, houseSnapshot.getString(Schema.Houses.OWNER_ID))) {
                        return fail(ItemError.NOT_ALLOWED);
                    }
                    return ref.collection(Schema.History.COLLECTION).get(Source.SERVER)
                            .onSuccessTask(history -> {
                                List<DocumentReference> refs = new ArrayList<>();
                                for (DocumentSnapshot document : history.getDocuments()) {
                                    refs.add(document.getReference());
                                }
                                return deleteHistoryThenItem(firestore, refs, ref, house);
                            });
                }));
        return translate(task);
    }

    private static Task<Void> deleteHistoryThenItem(FirebaseFirestore firestore, List<DocumentReference> history,
                                                    DocumentReference item, DocumentReference house) {
        Task<Void> chain = Tasks.forResult(null);
        for (int start = 0; start < history.size(); start += HISTORY_DELETE_CHUNK) {
            List<DocumentReference> chunk =
                    new ArrayList<>(history.subList(start, Math.min(history.size(), start + HISTORY_DELETE_CHUNK)));
            chain = chain.onSuccessTask(ignored -> firestore.runTransaction(tx -> {
                for (DocumentReference ref : chunk) {
                    tx.delete(ref);
                }
                return null;
            }));
        }
        return chain.onSuccessTask(ignored -> firestore.runTransaction(tx -> {
            if (!tx.get(item).exists()) {
                throw new ItemException.Rejection(ItemError.NOT_FOUND);
            }
            tx.delete(item);
            tx.update(house, Schema.Houses.ITEM_COUNT, FieldValue.increment(-1));
            return null;
        }));
    }

    // ---- Helpers ----

    private static CollectionReference items(FirebaseFirestore firestore, String houseId) {
        return houseRef(firestore, houseId).collection(Schema.Items.COLLECTION);
    }

    private static DocumentReference houseRef(FirebaseFirestore firestore, String houseId) {
        return firestore.collection(Schema.Houses.COLLECTION).document(houseId);
    }

    private static DocumentReference itemRef(FirebaseFirestore firestore, String houseId, String itemId) {
        return items(firestore, houseId).document(itemId);
    }

    @Nullable
    private static Item read(DocumentSnapshot document) {
        Map<String, Object> data = document.getData();
        return data == null ? null : ItemDocuments.toItem(document.getId(), data);
    }

    private static Item requireStored(DocumentSnapshot snapshot) {
        if (!snapshot.exists()) {
            throw new ItemException.Rejection(ItemError.NOT_FOUND);
        }
        Item stored = read(snapshot);
        if (stored == null) {
            throw new ItemException.Rejection(ItemError.UNKNOWN);
        }
        return stored;
    }

    private static void rejectConflict(Item stored, Item shown) {
        switch (ItemSchedule.conflict(stored, shown.occurrenceId, shown.revision)) {
            case ALREADY_COMPLETED:
                throw new ItemException.Rejection(ItemError.ALREADY_COMPLETED);
            case SCHEDULE_CHANGED:
                throw new ItemException.Rejection(ItemError.SCHEDULE_CHANGED);
            case NONE:
            default:
                break;
        }
    }

    /** The stored creation time, written back unchanged so the rules see createdAt as untouched. */
    private static Object createdAt(DocumentSnapshot snapshot) {
        Object createdAt = snapshot.get(Schema.Items.CREATED_AT);
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

    /**
     * Gives the conflict between the stored document and what the screen showed, or null when the
     * document is malformed.
     */
    private static ItemSchedule.Conflict conflictOf(DocumentSnapshot snapshot, String occurrenceId, int revision) {
        Item stored = read(snapshot);
        return stored == null ? null : ItemSchedule.conflict(stored, occurrenceId, revision);
    }

    /**
     * A change that loses a race may be refused by the security rules before the transaction is
     * retried, which would read as "no permission". On a refusal, the document is read again from
     * the server and the error says what actually changed.
     */
    static <T> Task<T> explainDenial(Task<T> task, DocumentReference ref,
                                     Function<DocumentSnapshot, ItemSchedule.Conflict> conflictOf) {
        return task.continueWithTask(done -> {
            if (done.isSuccessful() || classify(done.getException()) != ItemError.PERMISSION_DENIED) {
                return done;
            }
            return ref.get(Source.SERVER).continueWithTask(read -> {
                if (!read.isSuccessful()) {
                    return done;
                }
                DocumentSnapshot snapshot = read.getResult();
                ItemError cause = denialCause(snapshot.exists(), snapshot.exists() ? conflictOf.apply(snapshot) : null);
                return cause == ItemError.PERMISSION_DENIED ? done : Tasks.forException(new ItemException(cause));
            });
        });
    }

    /** What a refused change ran into, judged from the document as the server has it now. */
    static ItemError denialCause(boolean exists, @Nullable ItemSchedule.Conflict conflict) {
        if (!exists) {
            return ItemError.NOT_FOUND;
        }
        if (conflict == ItemSchedule.Conflict.ALREADY_COMPLETED) {
            return ItemError.ALREADY_COMPLETED;
        }
        if (conflict == ItemSchedule.Conflict.SCHEDULE_CHANGED) {
            return ItemError.SCHEDULE_CHANGED;
        }
        return ItemError.PERMISSION_DENIED;
    }

    private static <T> Task<T> translate(Task<T> task) {
        return task.continueWithTask(done -> done.isSuccessful()
                ? done
                : Tasks.forException(asItemException(done.getException())));
    }

    private static ItemException asItemException(Exception e) {
        return e instanceof ItemException ? (ItemException) e : new ItemException(classify(e), e);
    }

    static ItemError classify(Throwable e) {
        // Tasks and transactions may wrap the real failure.
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ItemException) {
                return ((ItemException) t).error;
            }
            if (t instanceof ItemException.Rejection) {
                return ((ItemException.Rejection) t).error;
            }
            if (t instanceof FirebaseNetworkException) {
                return ItemError.NETWORK;
            }
            if (t instanceof FirebaseFirestoreException) {
                return ItemError.fromFirestoreCode(((FirebaseFirestoreException) t).getCode().name());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return ItemError.UNKNOWN;
    }

    private static <T> Task<T> fail(ItemError error) {
        return Tasks.forException(new ItemException(error));
    }
}
