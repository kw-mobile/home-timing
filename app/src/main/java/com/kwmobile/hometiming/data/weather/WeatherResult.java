package com.kwmobile.hometiming.data.weather;

import com.kwmobile.hometiming.domain.DailyForecast;

import java.time.LocalDate;
import java.util.Map;

/** A forecast ready for the domain rules, and where it came from. */
public final class WeatherResult {
    public enum Source {
        /** Fetched just now. */
        NETWORK,
        /** The latest run, already stored. */
        CACHE,
        /** An older run, used because fetching failed. Show its age. */
        STALE_CACHE,
        /** Test weather mode: never present this as a real forecast. */
        TEST
    }

    /** From today on, in date order; pass to {@code LaundryAdvisor} and {@code SuggestionEngine}. */
    public final Map<LocalDate, DailyForecast> days;
    public final Source source;
    public final long fetchedAtMillis;
    /** Raw validated hours for temperature, sky and daily min/max display. */
    public final java.util.List<com.kwmobile.hometiming.domain.ForecastHour> hours;

    WeatherResult(Map<LocalDate, DailyForecast> days, Source source, long fetchedAtMillis) {
        this(days, source, fetchedAtMillis, java.util.Collections.emptyList());
    }

    WeatherResult(Map<LocalDate, DailyForecast> days, Source source, long fetchedAtMillis,
                  java.util.List<com.kwmobile.hometiming.domain.ForecastHour> hours) {
        this.hours = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(hours));
        this.days = days;
        this.source = source;
        this.fetchedAtMillis = fetchedAtMillis;
    }
}
