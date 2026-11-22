package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A weekly chore as screens see it (docs/DATA_MODEL.md chores). Immutable; build new states with
 * {@link ChoreSchedule}. Optional values are null or "" when not set.
 */
public final class Chore {
    public final String id;
    public final String name;
    public final String type;
    public final String categoryId;
    public final String category;
    public final boolean weatherSensitive;
    /** 1 = Monday … 7 = Sunday. */
    public final int weekday;
    /** The occurrence's original date from the weekly rule; moving it does not change this. */
    public final LocalDate plannedDueDate;
    /** The date this occurrence will actually be done. */
    public final LocalDate nextDueDate;
    public final String occurrenceId;
    public final int revision;
    /** The occurrence whose weather suggestion the user dismissed, or null. */
    public final String dismissedOccurrenceId;
    public final LocalDate lastDoneDate;
    public final String location;
    public final String memo;
    public final String createdBy;

    public Chore(String id, String name, String type, boolean weatherSensitive, int weekday,
                 LocalDate plannedDueDate, LocalDate nextDueDate, String occurrenceId, int revision,
                 String dismissedOccurrenceId, LocalDate lastDoneDate, String location, String memo,
                 String createdBy) {
        this(id, name, type, weatherSensitive, weekday, plannedDueDate, nextDueDate, occurrenceId,
                revision, dismissedOccurrenceId, lastDoneDate, location, memo, createdBy, "", "");
    }

    public Chore(String id, String name, String type, boolean weatherSensitive, int weekday,
                 LocalDate plannedDueDate, LocalDate nextDueDate, String occurrenceId, int revision,
                 String dismissedOccurrenceId, LocalDate lastDoneDate, String location, String memo,
                 String createdBy, String categoryId, String category) {
        this.categoryId = categoryId == null ? "" : categoryId;
        this.category = category == null ? "" : category;
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.weatherSensitive = weatherSensitive;
        this.weekday = weekday;
        this.plannedDueDate = Objects.requireNonNull(plannedDueDate, "plannedDueDate");
        this.nextDueDate = Objects.requireNonNull(nextDueDate, "nextDueDate");
        this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        this.revision = revision;
        this.dismissedOccurrenceId = dismissedOccurrenceId;
        this.lastDoneDate = lastDoneDate;
        this.location = location == null ? "" : location;
        this.memo = memo == null ? "" : memo;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
    }

    /** Whether the user dismissed the suggestion for the current occurrence. */
    public boolean isSuggestionDismissed() {
        return occurrenceId.equals(dismissedOccurrenceId);
    }
}
