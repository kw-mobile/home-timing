package com.kwmobile.hometiming.data.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.domain.CategoryCatalog;
import java.util.*;

/** Two small, acknowledged catalogs per house. All changes are online transactions. */
public final class CategoryRepository {
    public interface Listener {
        void onCatalog(CategoryCatalog catalog, boolean cache);
        void onError(Exception error);
    }
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private static CategoryRepository instance;
    public CategoryRepository(FirebaseFirestore db, FirebaseAuth auth) { this.db = db; this.auth = auth; }
    public static synchronized CategoryRepository getInstance() {
        if (instance == null) instance = BuildConfig.FIREBASE_CONFIGURED
                ? new CategoryRepository(FirebaseFirestore.getInstance(), FirebaseAuth.getInstance())
                : new CategoryRepository(null, null);
        return instance;
    }
    static DocumentReference reference(FirebaseFirestore db, String house, boolean chore) {
        return db.collection("houses").document(house).collection("categoryCatalogs").document(chore ? "chore" : "item");
    }
    private String uid() { return auth == null || auth.getCurrentUser() == null ? null : auth.getCurrentUser().getUid(); }
    public Subscription listen(String house, boolean chore, Listener listener) {
        if (db == null) { listener.onError(new CategoryException("NOT_CONFIGURED")); return Subscription.NONE; }
        String uid = uid();
        OperationScope.Token scope = uid == null ? null : OperationScope.getInstance().capture(uid, house);
        if (scope == null) { listener.onError(new CategoryException("NOT_SIGNED_IN")); return Subscription.NONE; }
        ListenerRegistration registration = reference(db, house, chore).addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
            if (!OperationScope.getInstance().isCurrent(scope, uid())) return;
            if (error != null) { listener.onError(error); return; }
            if (snapshot == null || snapshot.getMetadata().hasPendingWrites()) return;
            try { listener.onCatalog(snapshot.exists() ? decode(snapshot, chore) : null, snapshot.getMetadata().isFromCache()); }
            catch (RuntimeException invalid) { listener.onError(new CategoryException("INVALID_DATA")); }
        });
        return ItemTasks.subscription(scope, registration);
    }
    public Task<CategoryCatalog> initialize(String house, boolean chore) {
        return mutate(house, chore, 0, "INIT", "", "");
    }
    public Task<CategoryCatalog> add(String house, boolean chore, String name) {
        return mutate(house, chore, 0, "ADD", "c_" + UUID.randomUUID().toString().replace("-", ""), name.trim());
    }
    public Task<CategoryCatalog> rename(String house, CategoryCatalog shown, String id, String name) {
        return mutate(house, shown.chore, shown.revision, "RENAME", id, name.trim());
    }
    public Task<CategoryCatalog> delete(String house, CategoryCatalog shown, String id, String target) {
        return mutate(house, shown.chore, shown.revision, "DELETE", id, target);
    }
    /** Imports only observed legacy categories; retries never revive an identifier already retired. */
    public Task<CategoryCatalog> adopt(String house, boolean chore, String raw) {
        return mutate(house, chore, 0, "ADOPT", CategoryCatalog.legacyId(chore, raw), CategoryCatalog.legacyLabel(chore, raw));
    }
    private Task<CategoryCatalog> mutate(String house, boolean chore, long expected, String operation, String source, String value) {
        if (db == null) return Tasks.forException(new CategoryException("NOT_CONFIGURED"));
        String uid = uid();
        OperationScope.Token scope = uid == null ? null : OperationScope.getInstance().capture(uid, house);
        if (scope == null) return Tasks.forException(new CategoryException("NOT_SIGNED_IN"));
        return ItemTasks.transaction(db, scope, this::uid, tx -> {
            DocumentReference ref = reference(db, house, chore);
            DocumentSnapshot snapshot = tx.get(ref);
            if (!snapshot.exists()) {
                if (!"INIT".equals(operation)) throw new IllegalArgumentException("CHANGED");
                CategoryCatalog initial = CategoryCatalog.defaults(chore);
                tx.set(ref, fields(initial, uid, "INIT", "", ""));
                return initial;
            }
            CategoryCatalog current = decode(snapshot, chore);
            if ("INIT".equals(operation)) return current;
            if (expected != 0 && current.revision != expected) throw new IllegalArgumentException("CHANGED");
            CategoryCatalog next;
            String applied = operation, target = "";
            switch (operation) {
                case "ADD": next = current.add(source, value); break;
                case "RENAME":
                    if (value.equals(current.labels.get(source))) return current;
                    next = current.rename(source, value); break;
                case "DELETE": target = value; next = current.delete(source, target); break;
                case "ADOPT":
                    if (current.resolve(source) != null) return current;
                    target = current.idForName(value);
                    if (target == null) { applied = "ADD"; target = ""; next = current.add(source, value); }
                    else { applied = "ALIAS"; next = current.alias(source, target); }
                    break;
                default: throw new IllegalArgumentException("INVALID");
            }
            tx.set(ref, fields(next, uid, applied, source, target));
            return next;
        }).continueWithTask(done -> {
            if (!OperationScope.getInstance().isCurrent(scope, uid()))
                return Tasks.forException(new CategoryException("NOT_SIGNED_IN"));
            if (done.isSuccessful()) return done;
            Throwable error = done.getException();
            for (Throwable c = error; c != null; c = c.getCause()) {
                if (c instanceof IllegalArgumentException)
                    return Tasks.forException(new CategoryException(c.getMessage(), error));
                if (c == c.getCause()) break;
            }
            return Tasks.forException(new CategoryException(ItemTasks.classify(error).name(), error));
        });
    }
    static Map<String, Object> fields(CategoryCatalog catalog, String uid, String operation, String source, String target) {
        Map<String, Object> data = new HashMap<>();
        data.put("labels", catalog.labels); data.put("redirects", catalog.redirects);
        data.put("revision", catalog.revision); data.put("operation", operation);
        data.put("source", source); data.put("target", target);
        data.put("updatedBy", uid); data.put("updatedAt", FieldValue.serverTimestamp());
        return data;
    }
    static CategoryCatalog decode(DocumentSnapshot snapshot, boolean chore) {
        Map<String, Object> data = snapshot.getData();
        if (data == null || !(data.get("revision") instanceof Number)) throw new IllegalArgumentException("INVALID_DATA");
        return new CategoryCatalog(chore, ((Number) data.get("revision")).longValue(),
                strings(data.get("labels")), strings(data.get("redirects")));
    }
    private static Map<String, String> strings(Object raw) {
        if (!(raw instanceof Map)) throw new IllegalArgumentException("INVALID_DATA");
        Map<String, String> result = new TreeMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) raw).entrySet()) {
            if (!(e.getKey() instanceof String) || !(e.getValue() instanceof String))
                throw new IllegalArgumentException("INVALID_DATA");
            result.put((String) e.getKey(), (String) e.getValue());
        }
        return result;
    }
    /** Canonicalizes a newly assigned category in the same transaction as the record write. */
    static void assignment(Transaction tx, FirebaseFirestore db, String house, boolean chore, String id, String name)
            throws FirebaseFirestoreException {
        if (id == null || id.isEmpty()) return; // Legacy API callers remain supported by the rules.
        DocumentSnapshot snapshot = tx.get(reference(db, house, chore));
        if (!snapshot.exists()) throw new IllegalArgumentException("CHANGED");
        CategoryCatalog catalog = decode(snapshot, chore);
        if (!id.equals(catalog.resolve(id)) || !name.equals(catalog.labels.get(id)))
            throw new ItemException.Rejection(ItemError.SCHEDULE_CHANGED);
    }
}
