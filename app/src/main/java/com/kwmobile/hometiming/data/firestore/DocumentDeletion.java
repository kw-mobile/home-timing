package com.kwmobile.hometiming.data.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.data.auth.OperationScope;

import java.util.function.Supplier;

/**
 * Resumable online deletion for items and chores. First freeze the parent, then remove each record
 * with its verified count, and only then remove the parent with its house count. A retry can safely
 * repeat any stage, including after another client finished the deletion.
 */
final class DocumentDeletion {
    private static final int READ_PAGE_SIZE = 100;

    private DocumentDeletion() {
    }

    static Task<Void> delete(FirebaseFirestore db, DocumentReference parent, String records,
                             String houseCount, String houseMutation, String uid, Supplier<String> currentUid,
                             OperationScope.Token scope) {
        DocumentReference house = parent.getParent().getParent();
        return ItemTasks.transaction(db, scope, currentUid, tx -> {
            DocumentSnapshot stored = tx.get(parent);
            DocumentSnapshot home = tx.get(house);
            if (!stored.exists()) {
                return false;
            }
            if (!uid.equals(stored.getString(Schema.Items.CREATED_BY))
                    && !uid.equals(home.getString(Schema.Houses.OWNER_ID))) {
                throw new ItemException.Rejection(ItemError.NOT_ALLOWED);
            }
            RecordState.count(stored);
            if (!Boolean.TRUE.equals(stored.getBoolean(Schema.Records.DELETING))) {
                tx.update(parent, Schema.Records.DELETING, true,
                        Schema.Items.UPDATED_BY, uid, Schema.Items.UPDATED_AT, FieldValue.serverTimestamp());
            }
            return true;
        }).onSuccessTask(exists -> exists
                ? drain(db, parent, house, records, houseCount, houseMutation, uid, currentUid, scope)
                : Tasks.forResult(null));
    }

    private static Task<Void> drain(FirebaseFirestore db, DocumentReference parent, DocumentReference house,
                                     String records, String houseCount, String houseMutation, String uid,
                                     Supplier<String> currentUid, OperationScope.Token scope) {
        if (!OperationScope.getInstance().isCurrent(scope, currentUid.get())) {
            return Tasks.forException(new ItemException(ItemError.NOT_SIGNED_IN));
        }
        return parent.collection(records).limit(READ_PAGE_SIZE).get(Source.SERVER).onSuccessTask(page -> {
            ItemTasks.requireCurrent(scope, currentUid);
            if (page.isEmpty()) {
                return ItemTasks.transaction(db, scope, currentUid, tx -> {
                    DocumentSnapshot stored = tx.get(parent);
                    if (!stored.exists()) {
                        return null;
                    }
                    if (!Boolean.TRUE.equals(stored.getBoolean(Schema.Records.DELETING))
                            || RecordState.count(stored) != 0) {
                        throw new ItemException.Rejection(ItemError.UNKNOWN);
                    }
                    tx.delete(parent);
                    tx.update(house, houseCount, FieldValue.increment(-1), houseMutation, parent.getId());
                    return null;
                });
            }
            Task<Void> chain = Tasks.forResult(null);
            for (DocumentSnapshot record : page.getDocuments()) {
                chain = chain.onSuccessTask(ignored -> ItemTasks.transaction(db, scope, currentUid, tx -> {
                    DocumentSnapshot stored = tx.get(parent);
                    DocumentSnapshot child = tx.get(record.getReference());
                    if (!stored.exists() || !child.exists()) {
                        return null;
                    }
                    long count = RecordState.count(stored);
                    if (!Boolean.TRUE.equals(stored.getBoolean(Schema.Records.DELETING)) || count == 0) {
                        throw new ItemException.Rejection(ItemError.UNKNOWN);
                    }
                    tx.delete(record.getReference());
                    tx.update(parent, Schema.Records.COUNT, count - 1,
                            Schema.Records.MUTATION_ID, record.getId(), Schema.Items.UPDATED_BY, uid,
                            Schema.Items.UPDATED_AT, FieldValue.serverTimestamp());
                    return null;
                }));
            }
            return chain.onSuccessTask(ignored -> drain(db, parent, house, records,
                    houseCount, houseMutation, uid, currentUid, scope));
        });
    }
}
