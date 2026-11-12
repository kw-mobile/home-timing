package com.kwmobile.hometiming.ui.main;

import com.kwmobile.hometiming.domain.ForecastHour;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Selects display values, falling back to hourly temperature ranges when official extremes are absent. */
public final class WeatherDisplay {
    public static final class Day {
        public final LocalDate date;
        public Double low, high;
        public Integer pop;
        public ForecastHour forecast;
        private Day(LocalDate date) { this.date = date; }
    }

    public final ForecastHour current;
    public final Map<LocalDate, Day> days;

    public WeatherDisplay(List<ForecastHour> hours, LocalDateTime now) {
        Map<LocalDate, Day> days = new TreeMap<>();
        Map<LocalDate, Double> minTmp = new HashMap<>();
        Map<LocalDate, Double> maxTmp = new HashMap<>();
        ForecastHour next = null;
        for (ForecastHour hour : hours) {
            if (hour.date.isBefore(now.toLocalDate())) continue;
            Day day = days.get(hour.date);
            if (day == null) { day = new Day(hour.date); days.put(hour.date, day); }
            if (hour.minTemperature != null) day.low = hour.minTemperature;
            if (hour.maxTemperature != null) day.high = hour.maxTemperature;
            if (hour.pop != null) {
                day.pop = day.pop == null ? hour.pop : Math.max(day.pop, hour.pop);
            }
            if (hour.temperature != null) {
                Double curMin = minTmp.get(hour.date);
                minTmp.put(hour.date, curMin == null ? hour.temperature : Math.min(curMin, hour.temperature));
                Double curMax = maxTmp.get(hour.date);
                maxTmp.put(hour.date, curMax == null ? hour.temperature : Math.max(curMax, hour.temperature));
            }
            // A future day's icon represents noon, not an assertion about the entire day.
            if (day.forecast == null || Math.abs(hour.hour - 12) < Math.abs(day.forecast.hour - 12)) {
                day.forecast = hour;
            }
            if (hour.date.equals(now.toLocalDate()) && hour.hour >= now.getHour()
                    && (next == null || hour.hour < next.hour)) next = hour;
        }
        for (Map.Entry<LocalDate, Day> entry : days.entrySet()) {
            Day day = entry.getValue();
            if (day.low == null) day.low = minTmp.get(day.date);
            if (day.high == null) day.high = maxTmp.get(day.date);
        }
        Day today = days.get(now.toLocalDate());
        if (today != null) today.forecast = next;
        current = next;
        this.days = Collections.unmodifiableMap(days);
    }

    /** PTY wins over SKY; null never means clear. */
    public static String condition(ForecastHour hour) {
        if (hour == null || hour.pty == null) return "unknown";
        switch (hour.pty) {
            case 1: case 5: return "rain";
            case 2: case 6: return "sleet";
            case 3: case 7: return "snow";
            case 4: return "shower";
            case 0:
                if (hour.sky == null) return "unknown";
                if (hour.sky == 1) return "clear";
                if (hour.sky == 3) return "clouds";
                if (hour.sky == 4) return "overcast";
                return "unknown";
            default: return "unknown";
        }
    }
}
