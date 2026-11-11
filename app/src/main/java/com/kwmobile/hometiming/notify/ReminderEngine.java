package com.kwmobile.hometiming.notify;

import com.kwmobile.hometiming.domain.PlannedReminder;
import com.kwmobile.hometiming.domain.ReminderChange;
import com.kwmobile.hometiming.domain.ReminderDecision;
import com.kwmobile.hometiming.domain.ReminderKey;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The reminder state machine, free of Android types so every path is unit tested (docs/MVP.md
 * 날씨·알림 계약). Calls must be serialized; {@link ReminderManager} runs them on the main thread.
 */
final class ReminderEngine {
    interface Alarms {
        void set(PlannedReminder reminder);

        void cancel(ReminderKey key);
    }

    interface Display {
        void show(PlannedReminder reminder);

        void dismiss(String key);
    }

    private final ReminderLedger ledger;
    private final Alarms alarms;
    private final Display display;

    ReminderEngine(ReminderLedger ledger, Alarms alarms, Display display) {
        this.ledger = ledger;
        this.alarms = alarms;
        this.display = display;
    }

    /** Makes alarms match a fresh plan, cancelling whatever the plan no longer contains. */
    void apply(List<PlannedReminder> plan) {
        Set<String> planned = new HashSet<>();
        Set<String> occurrences = new HashSet<>();
        for (PlannedReminder reminder : plan) {
            planned.add(reminder.key.value());
            occurrences.add(occurrenceOf(reminder.key));
        }
        for (PlannedReminder old : ledger.allScheduled()) {
            if (!planned.contains(old.key.value())) {
                alarms.cancel(old.key);
                ledger.unschedule(old.key);
            }
        }
        for (String key : ledger.allFired()) {
            ReminderKey parsed = ReminderKey.parse(key);
            // A passed advance reminder leaves the plan while its occurrence is still running.
            if (parsed == null || !occurrences.contains(occurrenceOf(parsed))) {
                display.dismiss(key);
                ledger.forgetFired(key);
            }
        }
        for (PlannedReminder reminder : plan) {
            PlannedReminder old = ledger.scheduled(reminder.key);
            if (ledger.wasFired(reminder)) {
                if (old != null) {
                    alarms.cancel(reminder.key);
                    ledger.unschedule(reminder.key);
                }
            } else {
                ledger.schedule(reminder);
                if (old == null || old.fireAtMillis != reminder.fireAtMillis) {
                    alarms.set(reminder);
                }
            }
        }
    }

    /** After a reboot alarms are gone. One whose time passed meanwhile fires at once. */
    void rearm() {
        for (PlannedReminder reminder : ledger.allScheduled()) {
            alarms.set(reminder);
        }
    }

    /** The stored reminder for a firing alarm; null when it was already handled or cancelled. */
    PlannedReminder pending(ReminderKey key) {
        return ledger.scheduled(key);
    }

    /** @return whether a server sync is needed to schedule again */
    boolean onDecision(PlannedReminder reminder, ReminderDecision decision) {
        if (ledger.scheduled(reminder.key) == null) {
            return false;
        }
        switch (decision) {
            case FIRE:
            case UNCONFIRMED:
                display.show(reminder);
                ledger.markFired(reminder);
                return false;
            case RESCHEDULE:
                ledger.unschedule(reminder.key);
                return true;
            case CANCEL:
            default:
                ledger.unschedule(reminder.key);
                return false;
        }
    }

    /** @return whether a server sync is needed to schedule the new state */
    boolean onChanges(String houseId, Collection<ReminderChange> changes) {
        boolean resync = false;
        for (ReminderChange change : changes) {
            if (change.type == ReminderChange.Type.COMPLETED || change.type == ReminderChange.Type.DELETED) {
                cancelOccurrence(houseId, change);
            }
            resync |= change.type != ReminderChange.Type.DELETED;
        }
        return resync;
    }

    private void cancelOccurrence(String houseId, ReminderChange change) {
        for (ReminderKey.Type type : ReminderKey.Type.values()) {
            ReminderKey key = new ReminderKey(change.kind, houseId, change.documentId, type, change.previousOccurrenceId);
            alarms.cancel(key);
            ledger.unschedule(key);
            display.dismiss(key.value());
            ledger.forgetFired(key.value());
        }
    }

    /** Sign-out: nothing of this account may remain on the device. */
    void clear() {
        for (PlannedReminder reminder : ledger.allScheduled()) {
            alarms.cancel(reminder.key);
            display.dismiss(reminder.key.value());
        }
        for (String key : ledger.allFired()) {
            display.dismiss(key);
        }
        ledger.clear();
    }

    private static String occurrenceOf(ReminderKey key) {
        return key.kind + "/" + key.houseId + "/" + key.documentId + "/" + key.occurrenceId;
    }
}
