package com.kwmobile.hometiming.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.kwmobile.hometiming.data.firestore.ReminderRepository;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;

/** A reminder's time came: ask the server, then show, drop or schedule it again. */
public final class ReminderAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ReminderKey key = AndroidAlarms.keyOf(intent);
        if (key == null) {
            return;
        }
        ReminderManager manager = ReminderManager.getInstance(context);
        ReminderSession.Pending pending = manager.pending(key);
        if (pending == null) {
            return;
        }
        PendingResult result = goAsync();
        ReminderRepository.getInstance().checkBeforeFire(key, pending.reminder.revision).addOnCompleteListener(done -> {
            try {
                manager.onDecision(pending, done.isSuccessful() ? done.getResult() : ReminderDecision.UNCONFIRMED);
            } finally {
                result.finish();
            }
        });
    }
}
