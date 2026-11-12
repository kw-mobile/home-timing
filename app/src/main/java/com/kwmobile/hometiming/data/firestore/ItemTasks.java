package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.Transaction;
import com.kwmobile.hometiming.data.auth.OperationScope;
import com.kwmobile.hometiming.domain.ItemSchedule;

import java.util.function.Function;
import java.util.function.Supplier;

/** Shared failure handling for item, chore and reminder tasks. */
final class ItemTasks {
    private ItemTasks() {
    }

    static void requireCurrent(OperationScope.Token scope, Supplier<String> uid) {
        if (!OperationScope.getInstance().isCurrent(scope, uid.get())) {
            throw new ItemException.Rejection(ItemError.NOT_SIGNED_IN);
        }
    }

    static <T> Task<T> transaction(FirebaseFirestore db, OperationScope.Token scope, Supplier<String> uid,
                                    Transaction.Function<T> body) {
        return db.runTransaction(tx -> {
            requireCurrent(scope, uid);
            T result = body.apply(tx);
            // The transaction has only queued its writes; abort before commit if the scope changed.
            requireCurrent(scope, uid);
            return result;
        });
    }

    static <T> Task<T> scoped(Task<T> task, OperationScope.Token scope, Supplier<String> uid) {
        return task.continueWithTask(done -> {
            if (!OperationScope.getInstance().isCurrent(scope, uid.get())) {
                return Tasks.forException(new ItemException(ItemError.NOT_SIGNED_IN));
            }
            if (done.isSuccessful()) {
                return done;
            }
            Exception failure = done.getException();
            return Tasks.forException(failure instanceof ItemException ? failure
                    : new ItemException(classify(failure), failure));
        });
    }

    static Subscription subscription(OperationScope.Token scope, ListenerRegistration listener) {
        OperationScope.Registration invalidation = OperationScope.getInstance().onInvalidated(scope, listener::remove);
        return () -> {
            listener.remove();
            invalidation.remove();
        };
    }

    /** Re-reads a refused change to distinguish a conflict from a real permission denial. */
    static <T> Task<T> explainDenial(Task<T> task, DocumentReference ref,
                                    Function<DocumentSnapshot, ItemSchedule.Conflict> conflictOf,
                                    OperationScope.Token scope, Supplier<String> uid) {
        return task.continueWithTask(done -> {
            requireCurrent(scope, uid);
            if (done.isSuccessful() || classify(done.getException()) != ItemError.PERMISSION_DENIED) {
                return done;
            }
            return ref.get(Source.SERVER).continueWithTask(read -> {
                requireCurrent(scope, uid);
                if (!read.isSuccessful()) {
                    return done;
                }
                DocumentSnapshot snapshot = read.getResult();
                ItemError cause = denialCause(snapshot.exists(), snapshot.exists() ? conflictOf.apply(snapshot) : null);
                return cause == ItemError.PERMISSION_DENIED ? done : Tasks.forException(new ItemException(cause));
            });
        });
    }

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

    static ItemError classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ItemException) {
                return ((ItemException) cause).error;
            }
            if (cause instanceof ItemException.Rejection) {
                return ((ItemException.Rejection) cause).error;
            }
            if (cause instanceof FirebaseNetworkException) {
                return ItemError.NETWORK;
            }
            if (cause instanceof FirebaseFirestoreException) {
                return ItemError.fromFirestoreCode(((FirebaseFirestoreException) cause).getCode().name());
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return ItemError.UNKNOWN;
    }
}
