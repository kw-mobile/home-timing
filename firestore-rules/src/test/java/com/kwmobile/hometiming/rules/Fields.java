package com.kwmobile.hometiming.rules;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/** Document fields in the Firestore REST value format. */
final class Fields {
    private final Map<String, String> values = new LinkedHashMap<>();

    static Fields of() {
        return new Fields();
    }

    Fields str(String name, String value) {
        return put(name, "{\"stringValue\":" + Json.quote(value) + "}");
    }

    Fields num(String name, long value) {
        return put(name, "{\"integerValue\":\"" + value + "\"}");
    }

    Fields bool(String name, boolean value) {
        return put(name, "{\"booleanValue\":" + value + "}");
    }

    Fields time(String name, Instant value) {
        return put(name, "{\"timestampValue\":" + Json.quote(value.toString()) + "}");
    }

    Fields nul(String name) {
        return put(name, "{\"nullValue\":null}");
    }

    Iterable<String> names() {
        return values.keySet();
    }

    /** The {@code fields} map, e.g. {@code {"name":{"stringValue":"x"}}}. */
    String toJson() {
        StringJoiner joined = new StringJoiner(",", "{", "}");
        values.forEach((name, value) -> joined.add(Json.quote(name) + ":" + value));
        return joined.toString();
    }

    private Fields put(String name, String valueJson) {
        values.put(name, valueJson);
        return this;
    }
}
