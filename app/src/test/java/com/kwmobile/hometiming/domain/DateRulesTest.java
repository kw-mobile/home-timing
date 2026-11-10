package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.Test;

public class DateRulesTest {
    @Test
    public void todayUsesSeoulDateWhenUtcIsStillPreviousDay() {
        // 2026-11-10 15:30 UTC is 2026-11-11 00:30 in Seoul.
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:30:00Z"), ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 11, 11), DateRules.today(clock));
    }

    @Test
    public void todayStaysOnSeoulDateJustBeforeSeoulMidnight() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T14:59:59Z"), ZoneOffset.UTC);

        assertEquals(LocalDate.of(2026, 11, 10), DateRules.today(clock));
    }

    @Test
    public void todayIgnoresDeviceZone() {
        Instant instant = Instant.parse("2026-11-10T20:00:00Z"); // Seoul 11-11 05:00
        Clock newYork = Clock.fixed(instant, ZoneId.of("America/New_York"));

        assertEquals(LocalDate.of(2026, 11, 11), DateRules.today(newYork));
    }

    @Test
    public void parsesAndFormatsStorageDates() {
        assertEquals(LocalDate.of(2028, 2, 29), DateRules.parse("2028-02-29"));
        assertEquals("2026-01-05", DateRules.format(LocalDate.of(2026, 1, 5)));
        assertEquals("2026-12-31", DateRules.format(DateRules.parse("2026-12-31")));
    }

    @Test
    public void rejectsDatesThatDoNotExist() {
        assertRejected("2026-02-30");
        assertRejected("2027-02-29");
        assertRejected("2026-13-01");
        assertRejected("2026-00-10");
    }

    @Test
    public void rejectsNonStorageShapes() {
        assertRejected("2026-1-5");
        assertRejected("2026/01/05");
        assertRejected(" 2026-01-05");
        assertRejected("2026-01-05T00:00");
        assertRejected("");
        assertRejected(null);
    }

    private static void assertRejected(String text) {
        assertThrows(IllegalArgumentException.class, () -> DateRules.parse(text));
    }
}
