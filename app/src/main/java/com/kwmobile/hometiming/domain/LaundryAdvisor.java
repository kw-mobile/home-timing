package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.NavigableMap;
import java.util.TreeMap;

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
        NavigableMap<LocalDate, DailyForecast> ordered = new TreeMap<>(forecast);
        for (Map.Entry<LocalDate, DailyForecast> day : ordered.tailMap(due, false).entrySet()) {
            if (!day.getKey().isBefore(today) && judge(day.getValue()) == Verdict.GOOD) {
                return day.getKey();
            }
        }
        for (Map.Entry<LocalDate, DailyForecast> day : ordered.headMap(due, false).descendingMap().entrySet()) {
            if (day.getKey().isBefore(today)) {
                break;
            }
            if (judge(day.getValue()) == Verdict.GOOD) {
                return day.getKey();
            }
        }
        return null;
    }
}
