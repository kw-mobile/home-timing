package com.kwmobile.hometiming.notify;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Tasks;
import com.kwmobile.hometiming.data.firestore.ItemError;
import com.kwmobile.hometiming.data.firestore.ItemException;
import com.kwmobile.hometiming.data.firestore.ReminderRepository;
import com.kwmobile.hometiming.data.firestore.ReminderSnapshot;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Background sync: refreshes reminders only from a successful server read. */
public final class ReminderResyncWorker extends Worker {
    /** What a failed sync should do next. */
    enum Outcome {
        RETRY,
        /** Signed out: no reminders may remain. */
        CLEAR,
        /** Retrying cannot help; keep what is scheduled. */
        STOP
    }

    private static final long READ_TIMEOUT_SECONDS = 30;

    public ReminderResyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ReminderManager manager = ReminderManager.getInstance(getApplicationContext());
        ReminderSession.Sync sync = manager.beginSync();
        if (sync == null || isStopped()) {
            return Result.success();
        }
        try {
            ReminderSnapshot snapshot = Tasks.await(ReminderRepository.getInstance()
                            .readFromServer(sync.session.uid, sync.session.houseId),
                    READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!isStopped()) {
                manager.applySnapshot(sync, snapshot);
            }
            return Result.success();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            Outcome outcome = cause instanceof ItemException ? outcomeOf(((ItemException) cause).error) : Outcome.RETRY;
            if (outcome == Outcome.CLEAR) {
                manager.clear(sync.session);
            }
            return outcome == Outcome.RETRY ? Result.retry() : Result.success();
        } catch (TimeoutException e) {
            return Result.retry();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }
    }

    static Outcome outcomeOf(ItemError error) {
        switch (error) {
            case NOT_SIGNED_IN:
            case PERMISSION_DENIED:
            case NOT_FOUND:
                return Outcome.CLEAR;
            case NOT_CONFIGURED:
                return Outcome.STOP;
            default:
                return Outcome.RETRY;
        }
    }
}
