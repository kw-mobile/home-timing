package com.kwmobile.hometiming.domain;

/** A change to an item or chore that affects its reminders, from this or another device. */
public final class ReminderChange {
    public enum Type {
        /** New document: schedule its reminders. */
        ADDED,
        /** The occurrence finished: cancel {@link #previousOccurrenceId}, schedule {@link #occurrenceId}. */
        COMPLETED,
        /** Same occurrence, new schedule: replace its reminders. */
        RESCHEDULED,
        /** Removed: cancel {@link #previousOccurrenceId}. */
        DELETED
    }

    public final Type type;
    public final ReminderKey.Kind kind;
    public final String documentId;
    /** Null for {@link Type#ADDED}. */
    public final String previousOccurrenceId;
    /** Null for {@link Type#DELETED}. */
    public final String occurrenceId;

    ReminderChange(Type type, ReminderKey.Kind kind, String documentId, String previousOccurrenceId,
                   String occurrenceId) {
        this.type = type;
        this.kind = kind;
        this.documentId = documentId;
        this.previousOccurrenceId = previousOccurrenceId;
        this.occurrenceId = occurrenceId;
    }
}
