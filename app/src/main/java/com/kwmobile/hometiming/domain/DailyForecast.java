package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A day's drying-window forecast (docs/MVP.md 날씨·알림 계약). Laundry is judged on 09–18 h only.
 *
 * <p>{@link #complete} is false when a needed hour or value is missing; such a day is never assumed
 * suitable. The numbers are meaningful only when complete.
 */
public final class DailyForecast {
    public static final int DRYING_START_HOUR = 9;
    public static final int DRYING_END_HOUR = 18;

    public final LocalDate date;
    public final boolean complete;
    public final int maxPop;
    public final boolean hasPrecip;
    public final double avgReh;

    public DailyForecast(LocalDate date, boolean complete, int maxPop, boolean hasPrecip, double avgReh) {
        this.date = Objects.requireNonNull(date, "date");
        this.complete = complete;
        this.maxPop = maxPop;
        this.hasPrecip = hasPrecip;
        this.avgReh = avgReh;
    }

    /**
     * Summarizes parsed hours into days from today on, in date order.
     *
     * <p>Past days are dropped. Today needs only the hours still ahead; when the drying window is
     * already over, today is left out so it can never be proposed.
     *
     * @param currentHour the current hour of day in Asia/Seoul
     */
    public static Map<LocalDate, DailyForecast> summarize(List<ForecastHour> hours, LocalDate today, int currentHour) {
        Objects.requireNonNull(hours, "hours");
        Objects.requireNonNull(today, "today");
        Map<LocalDate, Map<Integer, ForecastHour>> byDate = new TreeMap<>();
        for (ForecastHour hour : hours) {
            if (!hour.date.isBefore(today)) {
                Map<Integer, ForecastHour> day = byDate.get(hour.date);
                if (day == null) {
                    day = new HashMap<>();
                    byDate.put(hour.date, day);
                }
                day.put(hour.hour, hour);
            }
        }
        Map<LocalDate, DailyForecast> days = new TreeMap<>();
        for (Map.Entry<LocalDate, Map<Integer, ForecastHour>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            int firstHour = date.equals(today) ? Math.max(DRYING_START_HOUR, currentHour + 1) : DRYING_START_HOUR;
            if (firstHour <= DRYING_END_HOUR) {
                days.put(date, summarizeDay(date, entry.getValue(), firstHour));
            }
        }
        return Collections.unmodifiableMap(days);
    }

    private static DailyForecast summarizeDay(LocalDate date, Map<Integer, ForecastHour> hours, int firstHour) {
        int maxPop = 0;
        int rehSum = 0;
        int count = 0;
        boolean precip = false;
        for (int hour = firstHour; hour <= DRYING_END_HOUR; hour++) {
            ForecastHour forecast = hours.get(hour);
            if (forecast == null || !forecast.isComplete()) {
                return new DailyForecast(date, false, 0, false, 0);
            }
            maxPop = Math.max(maxPop, forecast.pop);
            precip |= forecast.pty != 0;
            rehSum += forecast.reh;
            count++;
        }
        return new DailyForecast(date, true, maxPop, precip, (double) rehSum / count);
    }
}
