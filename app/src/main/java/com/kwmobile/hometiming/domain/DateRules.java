package com.kwmobile.hometiming.domain;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Calendar-date rules shared by every screen and repository.
 *
 * <p>Product dates are whole days in Asia/Seoul (docs/MVP.md) and are stored as {@code yyyy-MM-dd}
 * strings, so time of day and the device time zone never shift a due date.
 */
public final class DateRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private static final Pattern STORAGE_SHAPE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final DateTimeFormatter STORAGE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private DateRules() {
    }

    /** Today's date in Asia/Seoul, regardless of the clock's or device's zone. */
    public static LocalDate today(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        // LocalDate.ofInstant is Java 9 and only exists on Android API 34+; minSdk is 26.
        return LocalDate.now(clock.withZone(ZONE));
    }

    /**
     * Parses a stored {@code yyyy-MM-dd} date.
     *
     * @throws IllegalArgumentException if the text is missing, not zero-padded, or not a real date
     */
    public static LocalDate parse(String text) {
        if (text == null || !STORAGE_SHAPE.matcher(text).matches()) {
            throw new IllegalArgumentException("Expected yyyy-MM-dd: " + text);
        }
        try {
            return LocalDate.parse(text, STORAGE_FORMAT);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Not a calendar date: " + text, e);
        }
    }

    /** Formats a date for storage as {@code yyyy-MM-dd}. */
    public static String format(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return STORAGE_FORMAT.format(date);
    }
}
