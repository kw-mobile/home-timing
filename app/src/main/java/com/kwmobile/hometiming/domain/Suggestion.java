package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A weather card for one chore occurrence. Never stored: recomputed from the cached forecast, so it
 * disappears when the forecast changes. Carries the occurrence and revision it was computed from so
 * accepting a stale card cannot overwrite a newer schedule.
 */
public final class Suggestion {
    public enum Kind {
        /** Bad weather and a suitable day exists: propose {@link #target}. */
        MOVE,
        /** Bad weather and no suitable day in range: advise indoor drying. */
        INDOOR,
        /** Drying may be slow: notice only, nothing to accept. */
        CAUTION
    }

    public final Kind kind;
    public final String choreId;
    public final String occurrenceId;
    public final int revision;
    public final LocalDate from;
    /** Set only for {@link Kind#MOVE}. */
    public final LocalDate target;
    public final DailyForecast fromForecast;
    /** Set only for {@link Kind#MOVE}. */
    public final DailyForecast targetForecast;

    Suggestion(Kind kind, Chore chore, DailyForecast fromForecast, LocalDate target, DailyForecast targetForecast) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.choreId = chore.id;
        this.occurrenceId = chore.occurrenceId;
        this.revision = chore.revision;
        this.from = chore.nextDueDate;
        this.fromForecast = fromForecast;
        this.target = target;
        this.targetForecast = targetForecast;
    }
}
