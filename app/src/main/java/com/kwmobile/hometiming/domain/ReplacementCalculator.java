package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Replacement schedule rules (docs/MVP.md, F-04·F-05).
 *
 * <p>The next due date counts from the actual replacement date, never from the previous due date,
 * and weather never changes it.
 */
public final class ReplacementCalculator {
    public static final int MIN_CYCLE_DAYS = 1;
    public static final int MAX_CYCLE_DAYS = 730;
    /** Largest D-day still shown as {@link DueStatus#SOON}. */
    public static final int SOON_MAX_DAYS = 3;

    private ReplacementCalculator() {
    }

    public static boolean isValidCycle(int cycleDays) {
        return cycleDays >= MIN_CYCLE_DAYS && cycleDays <= MAX_CYCLE_DAYS;
    }

    /**
     * Next due date = last actual replacement date + cycle.
     *
     * @throws IllegalArgumentException if the cycle is outside 1–730 days
     */
    public static LocalDate nextDueDate(LocalDate lastReplacedDate, int cycleDays) {
        Objects.requireNonNull(lastReplacedDate, "lastReplacedDate");
        if (!isValidCycle(cycleDays)) {
            throw new IllegalArgumentException("cycleDays must be "
                    + MIN_CYCLE_DAYS + ".." + MAX_CYCLE_DAYS + ": " + cycleDays);
        }
        return lastReplacedDate.plusDays(cycleDays);
    }

    /** D-day = due date − today. Negative means overdue. */
    public static long daysUntil(LocalDate dueDate, LocalDate today) {
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(today, "today");
        return ChronoUnit.DAYS.between(today, dueDate);
    }

    public static DueStatus status(LocalDate dueDate, LocalDate today) {
        return statusForDaysUntil(daysUntil(dueDate, today));
    }

    public static DueStatus statusForDaysUntil(long daysUntil) {
        if (daysUntil < 0) {
            return DueStatus.OVERDUE;
        }
        if (daysUntil == 0) {
            return DueStatus.TODAY;
        }
        if (daysUntil <= SOON_MAX_DAYS) {
            return DueStatus.SOON;
        }
        return DueStatus.OK;
    }
}
