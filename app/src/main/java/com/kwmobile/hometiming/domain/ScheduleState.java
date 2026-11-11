package com.kwmobile.hometiming.domain;

/** The part of an item or chore that decides whether its reminders are still valid. */
public final class ScheduleState {
    public final ReminderKey.Kind kind;
    public final String documentId;
    public final String occurrenceId;
    public final int revision;

    public ScheduleState(ReminderKey.Kind kind, String documentId, String occurrenceId, int revision) {
        this.kind = kind;
        this.documentId = documentId;
        this.occurrenceId = occurrenceId;
        this.revision = revision;
    }

    public static ScheduleState of(Item item) {
        return new ScheduleState(ReminderKey.Kind.ITEM, item.id, item.occurrenceId, item.revision);
    }

    public static ScheduleState of(Chore chore) {
        return new ScheduleState(ReminderKey.Kind.CHORE, chore.id, chore.occurrenceId, chore.revision);
    }
}
