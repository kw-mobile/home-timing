package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.Test;

public class BaseTimeCalculatorTest {
    private static void assertRun(String date, String time, String now) {
        BaseTime run = BaseTimeCalculator.latest(LocalDateTime.parse(now));

        assertEquals(now, date, run.baseDate());
        assertEquals(now, time, run.baseTime());
    }

    @Test
    public void beforeTheFirstRunUsesYesterdaysLastRun() {
        assertRun("20261105", "2300", "2026-11-06T00:05:00");
        assertRun("20261105", "2300", "2026-11-06T02:05:00");
    }

    @Test
    public void aRunIsAvailableTenMinutesAfterItsHour() {
        assertRun("20261106", "0200", "2026-11-06T02:15:00");
        assertRun("20261106", "0200", "2026-11-06T05:09:00");
        assertRun("20261106", "0500", "2026-11-06T05:10:00");
    }

    @Test
    public void everyRunOfTheDay() {
        assertRun("20261106", "0800", "2026-11-06T10:59:00");
        assertRun("20261106", "1100", "2026-11-06T13:00:00");
        assertRun("20261106", "1400", "2026-11-06T16:30:00");
        assertRun("20261106", "1700", "2026-11-06T19:00:00");
        assertRun("20261106", "2000", "2026-11-06T22:00:00");
        assertRun("20261106", "2300", "2026-11-06T23:59:00");
    }

    @Test
    public void crossesMonthAndYearBoundaries() {
        assertRun("20261231", "2300", "2027-01-01T00:05:00");
        assertRun("20280229", "2300", "2028-03-01T01:00:00");
    }

    @Test
    public void clockIsReadInSeoul() {
        // 2026-11-05 15:30 UTC is 2026-11-06 00:30 in Seoul.
        BaseTime run = BaseTimeCalculator.latest(Clock.fixed(Instant.parse("2026-11-05T15:30:00Z"), ZoneOffset.UTC));

        assertEquals("20261105", run.baseDate());
        assertEquals("2300", run.baseTime());
    }
}
