package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

/**
 * Whether a day suits drying laundry, and which day to move to (PRD 6-2, docs/MVP.md). Weather only
 * ever affects weather-sensitive chores, never replacement dates.
 */
public final class LaundryAdvisor {
    public enum Verdict {
        GOOD,
        /** Drying may be slow: show a notice only. */
        CAUTION,
        /** Propose another day. */
        BAD,
        /** Needed forecast is missing; never treated as suitable. */
        UNKNOWN
    }

    static final int POP_BAD = 60;
    static final int POP_CAUTION = 30;
    static final int REH_CAUTION = 80;

    private LaundryAdvisor() {
    }

    public static Verdict judge(DailyForecast forecast) {
        if (forecast == null || !forecast.complete) {
            return Verdict.UNKNOWN;
        }
        if (forecast.maxPop >= POP_BAD || forecast.hasPrecip) {
            return Verdict.BAD;
        }
        if (forecast.maxPop >= POP_CAUTION || forecast.avgReh >= REH_CAUTION) {
            return Verdict.CAUTION;
        }
        return Verdict.GOOD;
    }

    /**
     * The nearest suitable day: later days first, then earlier ones back to today. Null means no
     * suitable day in range, so indoor drying is advised. Only GOOD days qualify.
     */
    public static LocalDate findAlternative(LocalDate due, Map<LocalDate, DailyForecast> forecast, LocalDate today) {
        Objects.requireNonNull(due, "due");
        Objects.requireNonNull(forecast, "forecast");
        Objects.requireNonNull(today, "today");
        for (LocalDate day = due.plusDays(1); forecast.containsKey(day); day = day.plusDays(1)) {
            if (judge(forecast.get(day)) == Verdict.GOOD) {
                return day;
            }
        }
        for (LocalDate day = due.minusDays(1); !day.isBefore(today) && forecast.containsKey(day); day = day.minusDays(1)) {
            if (judge(forecast.get(day)) == Verdict.GOOD) {
                return day;
            }
        }
        return null;
    }
}
