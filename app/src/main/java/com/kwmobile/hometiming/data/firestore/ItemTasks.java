package com.kwmobile.hometiming.data.firestore;

import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;
import com.kwmobile.hometiming.domain.ItemSchedule;

import java.util.function.Function;

/** Shared failure handling for item, chore and reminder tasks. */
final class ItemTasks {
    private ItemTasks() {
    }

    /** Re-reads a refused change to distinguish a conflict from a real permission denial. */
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
