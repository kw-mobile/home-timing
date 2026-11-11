package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/** One reminder to schedule: when it fires and what it says. */
public final class PlannedReminder {
    public final ReminderKey key;
    /** The revision it was planned from, sent to the server check before firing. */
    public final int revision;
    public final long fireAtMillis;
    public final LocalDate dueDate;
    public final String name;
    /** Days before the due date; 0 for the due-date reminder. */
    public final int daysBefore;

    public PlannedReminder(ReminderKey key, int revision, long fireAtMillis, LocalDate dueDate, String name,
                           int daysBefore) {
        this.key = Objects.requireNonNull(key, "key");
        this.revision = revision;
        this.fireAtMillis = fireAtMillis;
        this.dueDate = Objects.requireNonNull(dueDate, "dueDate");
        this.name = Objects.requireNonNull(name, "name");
        this.daysBefore = daysBefore;
    }
}
