package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/** One completed occurrence of an item (docs/DATA_MODEL.md history). */
public final class HistoryEntry {
    /** The finished occurrence; also the history document id. */
    public final String occurrenceId;
    public final LocalDate replacedDate;
    public final LocalDate previousDueDate;
    public final int cycleDaysUsed;
    public final String completedBy;
    /** Name at completion time, kept even if the member later leaves. */
    public final String completedByName;

    public HistoryEntry(String occurrenceId, LocalDate replacedDate, LocalDate previousDueDate,
                        int cycleDaysUsed, String completedBy, String completedByName) {
        this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        this.replacedDate = Objects.requireNonNull(replacedDate, "replacedDate");
        this.previousDueDate = Objects.requireNonNull(previousDueDate, "previousDueDate");
        this.cycleDaysUsed = cycleDaysUsed;
        this.completedBy = Objects.requireNonNull(completedBy, "completedBy");
        this.completedByName = Objects.requireNonNull(completedByName, "completedByName");
    }

    /** Replaced after the due date (positive) or before it (negative), in days. */
    public long daysLate() {
        return ReplacementCalculator.daysUntil(replacedDate, previousDueDate);
    }
}
