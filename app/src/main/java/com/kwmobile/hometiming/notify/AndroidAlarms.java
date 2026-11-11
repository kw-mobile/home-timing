package com.kwmobile.hometiming.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderKey;

/** Alarms through AlarmManager; falls back to inexact alarms without the exact-alarm permission. */
final class AndroidAlarms implements ReminderEngine.Alarms {
    private static final String KEY_PARAMETER = "key";

    private final Context context;
    private final AlarmManager manager;

    AndroidAlarms(Context context) {
        this.context = context;
        this.manager = context.getSystemService(AlarmManager.class);
    }

    @Override
    public void set(PlannedReminder reminder) {
        PendingIntent intent = pendingIntent(reminder.key, PendingIntent.FLAG_UPDATE_CURRENT);
        if (canScheduleExact(manager)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtMillis, intent);
                return;
            } catch (SecurityException e) {
                // The permission can be revoked between the check and the call.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtMillis, intent);
    }

    @Override
    public void cancel(ReminderKey key) {
        PendingIntent intent = pendingIntent(key, PendingIntent.FLAG_NO_CREATE);
        if (intent != null) {
            manager.cancel(intent);
            intent.cancel();
        }
    }

    static boolean canScheduleExact(AlarmManager manager) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms();
    }

    // The key goes in the data URI: alarms are told apart by it, not by extras.
    private PendingIntent pendingIntent(ReminderKey key, int flags) {
        Uri data = new Uri.Builder().scheme("hometiming").authority("reminder")
                .appendQueryParameter(KEY_PARAMETER, key.value()).build();
        Intent intent = new Intent(context, ReminderAlarmReceiver.class).setData(data);
        return PendingIntent.getBroadcast(context, 0, intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    static ReminderKey keyOf(Intent intent) {
        Uri data = intent.getData();
        return data == null ? null : ReminderKey.parse(data.getQueryParameter(KEY_PARAMETER));
    }
}
