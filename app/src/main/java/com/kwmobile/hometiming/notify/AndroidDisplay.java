package com.kwmobile.hometiming.notify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderKey;
import com.kwmobile.hometiming.ui.main.MainActivity;

/** Shows reminders. The key is the notification tag, so one can be dismissed by occurrence. */
final class AndroidDisplay implements ReminderEngine.Display {
    private static final String CHANNEL_ID = "reminders";
    private static final int NOTIFICATION_ID = 1;

    private final Context context;
    private final NotificationManager manager;

    AndroidDisplay(Context context) {
        this.context = context;
        this.manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT));
    }

    @Override
    public void show(PlannedReminder reminder) {
        if (!manager.areNotificationsEnabled()) {
            return;
        }
        Intent open = new Intent(context, MainActivity.class)
                .setData(android.net.Uri.parse("hometiming://open/" + reminder.key.kind + "/" + reminder.key.documentId))
                .putExtra(ReminderManager.EXTRA_KIND, reminder.key.kind.name())
                .putExtra(ReminderManager.EXTRA_DOCUMENT_ID, reminder.key.documentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(reminder.key.value(), NOTIFICATION_ID,
                new NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bell)
                        .setContentTitle(reminder.name)
                        .setContentText(text(reminder))
                        .setContentIntent(content)
                        .setAutoCancel(true)
                        .build());
    }

    private String text(PlannedReminder reminder) {
        boolean item = reminder.key.kind == ReminderKey.Kind.ITEM;
        if (reminder.key.type == ReminderKey.Type.ADVANCE) {
            return context.getString(item ? R.string.reminder_item_advance : R.string.reminder_chore_advance,
                    reminder.daysBefore);
        }
        return context.getString(item ? R.string.reminder_item_due : R.string.reminder_chore_due);
    }

    @Override
    public void dismiss(String key) {
        manager.cancel(key, NOTIFICATION_ID);
    }
}
