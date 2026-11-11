package com.kwmobile.hometiming.data.weather;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checks a short-range forecast response and turns it into forecast hours (docs/MVP.md 날씨·알림
 * 계약). A response that is the wrong run, wrong grid, truncated or malformed is refused as a
 * whole. A single missing or out-of-range value stays null, so that day is judged "unknown".
 */
final class KmaForecastParser {
    private static final String POP = "POP";
    private static final String PTY = "PTY";
    private static final String REH = "REH";
    private static final int PTY_MAX = 7;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private KmaForecastParser() {
    }

    static List<ForecastHour> parse(String json, BaseTime base, int nx, int ny) throws WeatherException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("result")) {
                throw new WeatherException(fromHubStatus(root.getAsJsonObject("result").get("status").getAsInt()));
            }
            JsonObject response = root.getAsJsonObject("response");
            String code = response.getAsJsonObject("header").get("resultCode").getAsString();
            if (!"00".equals(code)) {
                throw new WeatherException(fromResultCode(code));
            }
            JsonObject body = response.getAsJsonObject("body");
            int total = body.get("totalCount").getAsInt();
            if (total == 0) {
                throw new WeatherException(WeatherError.NO_DATA);
            }
            JsonArray items = body.getAsJsonObject("items").getAsJsonArray("item");
            if (items.size() != total) {
                throw invalid();
            }
            return readItems(items, base, nx, ny);
        } catch (RuntimeException e) {
            // Gson reports every shape problem (not JSON, missing node, wrong type) unchecked.
            throw invalid();
        }
    }

    private static List<ForecastHour> readItems(JsonArray items, BaseTime base, int nx, int ny)
            throws WeatherException {
        Map<String, Integer[]> slots = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            if (!base.baseDate().equals(item.get("baseDate").getAsString())
                    || !base.baseTime().equals(item.get("baseTime").getAsString())
                    || item.get("nx").getAsInt() != nx
                    || item.get("ny").getAsInt() != ny) {
                throw invalid();
            }
            String date = item.get("fcstDate").getAsString();
            String time = item.get("fcstTime").getAsString();
            LocalDate.parse(date, DATE);
            if (!isWholeHour(time)) {
                throw invalid();
            }
            String category = item.get("category").getAsString();
            int index = slotOf(category);
            if (index < 0) {
                continue;
            }
            if (!seen.add(date + time + category)) {
                throw invalid();
            }
            categories.add(category);
            Integer[] slot = slots.get(date + time);
            if (slot == null) {
                slot = new Integer[3];
                slots.put(date + time, slot);
            }
            slot[index] = value(item.get("fcstValue"), category.equals(PTY) ? PTY_MAX : 100);
        }
        if (categories.size() != 3) {
            throw invalid();
        }
        List<ForecastHour> hours = new ArrayList<>(slots.size());
        for (Map.Entry<String, Integer[]> entry : slots.entrySet()) {
            LocalDate date = LocalDate.parse(entry.getKey().substring(0, 8), DATE);
            int hour = Integer.parseInt(entry.getKey().substring(8, 10));
            Integer[] slot = entry.getValue();
            hours.add(new ForecastHour(date, hour, slot[0], slot[1], slot[2]));
        }
        return Collections.unmodifiableList(hours);
    }

    private static int slotOf(String category) {
        switch (category) {
            case POP:
                return 0;
            case PTY:
                return 1;
            case REH:
                return 2;
            default:
                return -1;
        }
    }

    private static boolean isWholeHour(String time) {
        if (time.length() != 4 || !time.endsWith("00")) {
            return false;
        }
        int hour = Integer.parseInt(time.substring(0, 2));
        return hour >= 0 && hour <= 23;
    }

    /** Null for a missing value, KMA's missing markers (±900 and beyond) and anything out of range. */
    private static Integer value(JsonElement element, int max) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            int value = Integer.parseInt(element.getAsString().trim());
            return value >= 0 && value <= max ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static WeatherError fromHubStatus(int status) {
        return status == 401 || status == 403 ? WeatherError.NOT_AUTHORIZED : WeatherError.SERVER;
    }

    private static WeatherError fromResultCode(String code) {
        switch (code) {
            case "03":
                return WeatherError.NO_DATA;
            case "20":
            case "21":
            case "30":
            case "31":
            case "32":
            case "33":
                return WeatherError.NOT_AUTHORIZED;
            default:
                return WeatherError.SERVER;
        }
    }

    private static WeatherException invalid() {
        return new WeatherException(WeatherError.INVALID_RESPONSE);
    }
}
