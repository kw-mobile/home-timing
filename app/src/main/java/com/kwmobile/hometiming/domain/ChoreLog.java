package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/** One completion or date move of a chore (docs/DATA_MODEL.md logs). */
public final class ChoreLog {
    public final String action;
    public final String occurrenceId;
    public final LocalDate date;
    /** Set for a date move. */
    public final LocalDate fromDate;
    /** Set for a date move: why it moved. */
    public final String reason;
    public final String actorId;
    /** Name at the time, kept even if the member later leaves. */
    public final String actorName;

    public ChoreLog(String action, String occurrenceId, LocalDate date, LocalDate fromDate, String reason,
                    String actorId, String actorName) {
        this.action = Objects.requireNonNull(action, "action");
        this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        this.date = Objects.requireNonNull(date, "date");
        this.fromDate = fromDate;
        this.reason = reason;
        this.actorId = Objects.requireNonNull(actorId, "actorId");
        this.actorName = Objects.requireNonNull(actorName, "actorName");
    }
}
