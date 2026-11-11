package com.kwmobile.hometiming.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** How server state affects scheduled reminders (docs/MVP.md 날씨·알림 계약). */
public final class ReminderRules {
    /**
     * Turns successive lists of one kind into changes. The first list only sets the baseline:
     * reminders that predate it are reconciled by a full server read, not by events.
     */
    public static final class Tracker {
        private Map<String, ScheduleState> known;

        public List<ReminderChange> update(Collection<ScheduleState> states) {
            Map<String, ScheduleState> next = new LinkedHashMap<>();
            for (ScheduleState state : states) {
                next.put(state.documentId, state);
            }
            Map<String, ScheduleState> previous = known;
            known = next;
            return previous == null ? Collections.<ReminderChange>emptyList() : diff(previous, next);
        }
    }

    private ReminderRules() {
    }

    /**
     * @param stored the server's current state; null when the document no longer exists
     * @param occurrenceId the occurrence the reminder was scheduled for
     * @param revision the revision it was scheduled from
     */
    public static ReminderDecision check(ScheduleState stored, String occurrenceId, int revision) {
        if (stored == null || !stored.occurrenceId.equals(occurrenceId)) {
            return ReminderDecision.CANCEL;
        }
        return stored.revision == revision ? ReminderDecision.FIRE : ReminderDecision.RESCHEDULE;
    }

    private static List<ReminderChange> diff(Map<String, ScheduleState> previous, Map<String, ScheduleState> next) {
        List<ReminderChange> changes = new ArrayList<>();
        for (ScheduleState now : next.values()) {
            ScheduleState before = previous.get(now.documentId);
            if (before == null) {
                changes.add(new ReminderChange(ReminderChange.Type.ADDED, now.kind, now.documentId, null,
                        now.occurrenceId));
            } else if (!before.occurrenceId.equals(now.occurrenceId)) {
                changes.add(new ReminderChange(ReminderChange.Type.COMPLETED, now.kind, now.documentId,
                        before.occurrenceId, now.occurrenceId));
            } else if (before.revision != now.revision) {
                changes.add(new ReminderChange(ReminderChange.Type.RESCHEDULED, now.kind, now.documentId,
                        before.occurrenceId, now.occurrenceId));
            }
        }
        for (ScheduleState before : previous.values()) {
            if (!next.containsKey(before.documentId)) {
                changes.add(new ReminderChange(ReminderChange.Type.DELETED, before.kind, before.documentId,
                        before.occurrenceId, null));
            }
        }
        return Collections.unmodifiableList(changes);
    }
}
