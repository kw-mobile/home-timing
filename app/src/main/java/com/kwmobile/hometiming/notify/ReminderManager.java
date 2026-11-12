package com.kwmobile.hometiming.notify;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.kwmobile.hometiming.data.firestore.ItemError;
import com.kwmobile.hometiming.data.firestore.ReminderRepository;
import com.kwmobile.hometiming.data.firestore.ReminderSnapshot;
import com.kwmobile.hometiming.data.firestore.Subscription;
import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderChange;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.domain.ReminderPlanner;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for local reminders (docs/MVP.md F-06). Screens call {@link #onSessionStarted} after
 * sign-in or a house change, {@link #onSessionEnded} on sign-out and {@link #requestSync} after the
 * personal settings change. Every state change runs on the main thread.
 */
public final class ReminderManager {
    /** Intent extras of an opened reminder: {@code ReminderKey.Kind} name and document id. */
    public static final String EXTRA_KIND = "com.kwmobile.hometiming.extra.KIND";
    public static final String EXTRA_DOCUMENT_ID = "com.kwmobile.hometiming.extra.DOCUMENT_ID";

    private static final String PREFS = "reminders";
    private static final String WORK_ONCE = "reminder_resync_once";
    private static final String WORK_PERIODIC = "reminder_resync_periodic";
    private static final long SYNC_PERIOD_HOURS = 12;
    private static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);

    private static volatile ReminderManager instance;

    private final WorkManager work;
    private final NotificationManager notifications;
    private final AlarmManager alarms;
    private final String packageName;
    private final ReminderSession session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Subscription changes = Subscription.NONE;

    private ReminderManager(Context context) {
        work = WorkManager.getInstance(context);
        notifications = context.getSystemService(NotificationManager.class);
        alarms = context.getSystemService(AlarmManager.class);
        packageName = context.getPackageName();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ReminderLedger ledger = new ReminderLedger(new ReminderLedger.Store() {
            @Override
            public String get(String key) {
                return prefs.getString(key, null);
            }

            @Override
            public void put(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }
        });
        ReminderEngine engine = new ReminderEngine(ledger, new AndroidAlarms(context), new AndroidDisplay(context));
        session = new ReminderSession(engine, new ReminderSession.BindingStore() {
            @Override
            public String uid() { return prefs.getString("owner_uid", null); }

            @Override
            public String houseId() { return prefs.getString("owner_house", null); }

            @Override
            public void save(String uid, String houseId) {
                prefs.edit().putString("owner_uid", uid).putString("owner_house", houseId).apply();
            }
        }, () -> ReminderRepository.getInstance().currentUid());
    }

    @NonNull
    public static ReminderManager getInstance(@NonNull Context context) {
        ReminderManager local = instance;
        if (local == null) {
            synchronized (ReminderManager.class) {
                local = instance;
                if (local == null) {
                    local = new ReminderManager(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    // ---- For screens ----

    /** Starts following a house and syncs. Call again with the new id after a house change. */
    public void onSessionStarted(@NonNull String houseId) {
        onMain(() -> {
            changes.remove();
            ReminderSession.Token token = session.start(houseId);
            if (token == null) {
                changes = Subscription.NONE;
                return;
            }
            changes = ReminderRepository.getInstance().listenChanges(houseId, new ReminderRepository.ChangesListener() {
                @Override
                public void onChanges(@NonNull List<ReminderChange> list) {
                    onMain(() -> {
                        if (session.onChanges(token, list)) {
                            requestSync();
                        }
                    });
                }

                @Override
                public void onError(@NonNull ItemError error) {
                    // Keep the last synced schedule on network failure. Removed membership is final.
                    if (error == ItemError.PERMISSION_DENIED) {
                        clear(token);
                    }
                }
            });
            work.enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                    new PeriodicWorkRequest.Builder(ReminderResyncWorker.class, SYNC_PERIOD_HOURS, TimeUnit.HOURS)
                            .setConstraints(online()).build());
            requestSync();
        });
    }

    /** Sign-out: invalidates in-flight responses as well as the stored schedule. */
    public void onSessionEnded() {
        onMain(() -> {
            session.end();
            changes.remove();
            changes = Subscription.NONE;
            work.cancelUniqueWork(WORK_PERIODIC);
            work.cancelUniqueWork(WORK_ONCE);
        });
    }

    /** Rebuilds reminders from the server, for example after the personal settings change. */
    public void requestSync() {
        onMain(() -> {
            session.clearIfAccountChanged();
            session.invalidateSync();
            if (session.capture() != null) {
                work.enqueueUniqueWork(WORK_ONCE, ExistingWorkPolicy.REPLACE,
                        new OneTimeWorkRequest.Builder(ReminderResyncWorker.class).setConstraints(online()).build());
            }
        });
    }

    public boolean canShowNotifications() {
        return notifications.areNotificationsEnabled();
    }

    /** False means reminders still arrive, but may be late: tell the user. */
    public boolean canScheduleExactAlarms() {
        return AndroidAlarms.canScheduleExact(alarms);
    }

    /** Opens the system page for the exact-alarm permission; null before Android 12. */
    @Nullable
    public Intent exactAlarmSettingsIntent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        return new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + packageName));
    }

    // ---- For receivers and the worker ----

    @Nullable
    ReminderSession.Pending pending(ReminderKey key) {
        return session.pending(key);
    }

    void onDecision(ReminderSession.Pending pending, ReminderDecision decision) {
        onMain(() -> {
            if (session.onDecision(pending, decision)) {
                requestSync();
            }
        });
    }

    void rearm() {
        onMain(() -> {
            session.rearm();
            requestSync();
        });
    }

    @Nullable
    ReminderSession.Sync beginSync() {
        ReminderSession.Sync sync = session.beginSync();
        if (sync == null) {
            onMain(session::clearIfAccountChanged);
        }
        return sync;
    }

    void applySnapshot(ReminderSession.Sync sync, ReminderSnapshot snapshot) {
        LocalTime parsed = ReminderPlanner.parseTime(snapshot.notifyTime);
        List<PlannedReminder> plan = ReminderPlanner.plan(snapshot.houseId, snapshot.items, snapshot.chores,
                parsed == null ? DEFAULT_TIME : parsed, snapshot.advanceDays, Clock.systemUTC());
        onMain(() -> session.apply(sync, snapshot.houseId, plan));
    }

    void clear(ReminderSession.Token token) {
        onMain(() -> {
            if (session.isCurrent(token)) {
                onSessionEnded();
            }
        });
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == main.getLooper()) {
            action.run();
        } else {
            main.post(action);
        }
    }

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }
}
