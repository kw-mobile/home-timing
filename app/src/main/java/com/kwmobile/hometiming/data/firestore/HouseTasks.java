package com.kwmobile.hometiming.data.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.kwmobile.hometiming.data.auth.OperationScope;

import java.util.function.Supplier;

/** Session checks and HouseError translation shared by house and invitation operations. */
final class HouseTasks {
    private final Supplier<String> currentUid;

    HouseTasks(Supplier<String> currentUid) {
        this.currentUid = currentUid;
    }

    void requireCurrent(OperationScope.Token scope) {
        if (!OperationScope.getInstance().isCurrent(scope, currentUid.get())) {
            throw new HouseException.Rejection(HouseError.NOT_SIGNED_IN);
        }
    }

    <T> Task<T> transaction(FirebaseFirestore firestore, OperationScope.Token scope,
                            Transaction.Function<T> body) {
        return firestore.runTransaction(tx -> {
            requireCurrent(scope);
            T result = body.apply(tx);
            requireCurrent(scope);
            return result;
        });
    }

    <T> Task<T> scoped(Task<T> task, OperationScope.Token scope) {
        return task.continueWithTask(done -> OperationScope.getInstance().isCurrent(scope, currentUid.get())
                ? done : fail(HouseError.NOT_SIGNED_IN));
    }

    Task<String> moved(Task<String> task, OperationScope.Token scope) {
        return task.continueWithTask(done -> {
            if (!done.isSuccessful()) {
                return OperationScope.getInstance().isCurrent(scope, currentUid.get())
                        ? done : fail(HouseError.NOT_SIGNED_IN);
            }
            return OperationScope.getInstance().move(scope, currentUid.get(), done.getResult())
                    ? done : fail(HouseError.NOT_SIGNED_IN);
        });
    }

    static <T> Task<T> translate(Task<T> task) {
        return task.continueWithTask(done -> done.isSuccessful()
                ? done
                : Tasks.forException(asHouseException(done.getException())));
    }

    /** Joining: a rules refusal means the house filled up or the code changed after the checks. */
    static <T> Task<T> translateJoin(Task<T> task) {
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

    static <T> Task<T> fail(HouseError error) {
        return Tasks.forException(new HouseException(error));
    }
}
