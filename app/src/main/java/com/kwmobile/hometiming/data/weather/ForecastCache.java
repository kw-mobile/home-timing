package com.kwmobile.hometiming.data.weather;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** The last forecast of each grid, with the run it came from. Corrupt entries read as absent. */
final class ForecastCache {
    interface Store {
        String get(String key);

        void put(String key, String value);
    }

    static final class Entry {
        final String baseDate;
        final String baseTime;
        final long fetchedAtMillis;
        final List<ForecastHour> hours;

        Entry(String baseDate, String baseTime, long fetchedAtMillis, List<ForecastHour> hours) {
            this.baseDate = baseDate;
            this.baseTime = baseTime;
            this.fetchedAtMillis = fetchedAtMillis;
            this.hours = hours;
        }

        boolean isRun(BaseTime base) {
            return baseDate.equals(base.baseDate()) && baseTime.equals(base.baseTime());
        }
    }

    private final Store store;

    ForecastCache(Store store) {
        this.store = store;
    }

    private static String key(int nx, int ny) {
        return String.format(Locale.ROOT, "forecast_display_v2_%d_%d", nx, ny);
    }

    void write(int nx, int ny, BaseTime base, long fetchedAtMillis, List<ForecastHour> hours) {
        JsonArray array = new JsonArray();
        for (ForecastHour hour : hours) {
            JsonObject object = new JsonObject();
            object.addProperty("d", DateRules.format(hour.date));
            object.addProperty("h", hour.hour);
            object.add("p", number(hour.pop));
            object.add("t", number(hour.pty));
            object.add("r", number(hour.reh));
            object.add("tmp", number(hour.temperature));
            object.add("sky", number(hour.sky));
            object.add("min", number(hour.minTemperature));
            object.add("max", number(hour.maxTemperature));
            array.add(object);
        }
        JsonObject root = new JsonObject();
        root.addProperty("baseDate", base.baseDate());
        root.addProperty("baseTime", base.baseTime());
        root.addProperty("fetchedAt", fetchedAtMillis);
        root.add("hours", array);
        store.put(key(nx, ny), root.toString());
    }

    /** Null when nothing usable is stored for this grid. */
    Entry read(int nx, int ny) {
        String stored = store.get(key(nx, ny));
        if (stored == null) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(stored).getAsJsonObject();
            List<ForecastHour> hours = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("hours")) {
                JsonObject object = element.getAsJsonObject();
                hours.add(new ForecastHour(DateRules.parse(object.get("d").getAsString()), object.get("h").getAsInt(),
                        integer(object.get("p")), integer(object.get("t")), integer(object.get("r")),
                        decimal(object.get("tmp")), integer(object.get("sky")),
                        decimal(object.get("min")), decimal(object.get("max"))));
            }
            return new Entry(root.get("baseDate").getAsString(), root.get("baseTime").getAsString(),
                    root.get("fetchedAt").getAsLong(), Collections.unmodifiableList(hours));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Double decimal(JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.getAsDouble();
    }

    private static JsonElement number(Number value) {
        return value == null ? JsonNull.INSTANCE : new JsonPrimitive(value);
    }

    private static Integer integer(JsonElement element) {
        return element == null || element.isJsonNull() ? null : Integer.valueOf(element.getAsInt());
    }
}
