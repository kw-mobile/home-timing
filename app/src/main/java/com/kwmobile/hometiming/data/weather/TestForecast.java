package com.kwmobile.hometiming.data.weather;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads assets/test_forecast.json. Days are offsets from today, so a demo works on any date; each
 * day's values apply to all its hours, and a null value makes that day "unknown".
 */
final class TestForecast {
    private TestForecast() {
    }

    static List<ForecastHour> hours(String json, LocalDate today) throws WeatherException {
        try {
            List<ForecastHour> hours = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("days")) {
                JsonObject day = element.getAsJsonObject();
                LocalDate date = today.plusDays(day.get("dayOffset").getAsInt());
                for (int hour = 0; hour < 24; hour++) {
                    hours.add(new ForecastHour(date, hour, integer(day.get("pop")), integer(day.get("pty")),
                            integer(day.get("reh")), decimal(day.get("tmp")), integer(day.get("sky")),
                            decimal(day.get("min")), decimal(day.get("max"))));
                }
            }
            return hours;
        } catch (RuntimeException e) {
            throw new WeatherException(WeatherError.INVALID_RESPONSE);
        }
    }

    private static Double decimal(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsDouble();
    }

    private static Integer integer(JsonElement element) {
        return element == null || element.isJsonNull() ? null : Integer.valueOf(element.getAsInt());
    }
}
