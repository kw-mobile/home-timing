package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.time.LocalDate;

import org.junit.Test;

public class ReplacementCalculatorTest {
    @Test
    public void sixtyDayCycleFromNovemberFirstIsDecemberThirtyFirst() {
        // PRD scenario A: shower filter installed 11/1 with a 60-day cycle.
        assertEquals(LocalDate.of(2026, 12, 31),
                ReplacementCalculator.nextDueDate(LocalDate.of(2026, 11, 1), 60));
    }

    @Test
    public void countsWholeDaysAcrossMonthEnds() {
        assertEquals(LocalDate.of(2026, 3, 2),
                ReplacementCalculator.nextDueDate(LocalDate.of(2026, 1, 31), 30));
        assertEquals(LocalDate.of(2027, 1, 1),
                ReplacementCalculator.nextDueDate(LocalDate.of(2026, 12, 31), 1));
    }

    @Test
    public void countsLeapDay() {
        assertEquals(LocalDate.of(2028, 3, 1),
                ReplacementCalculator.nextDueDate(LocalDate.of(2028, 1, 31), 30));
        assertEquals(LocalDate.of(2028, 2, 29),
                ReplacementCalculator.nextDueDate(LocalDate.of(2028, 2, 28), 1));
        assertEquals(LocalDate.of(2029, 2, 28),
                ReplacementCalculator.nextDueDate(LocalDate.of(2028, 2, 29), 365));
    }

    @Test
    public void lateReplacementCountsFromActualDateNotPreviousDueDate() {
        // Due 12/31 but actually replaced 1/3: the next cycle starts from 1/3.
        LocalDate actual = LocalDate.of(2027, 1, 3);

        assertEquals(LocalDate.of(2027, 3, 4), ReplacementCalculator.nextDueDate(actual, 60));
    }

    @Test
    public void acceptsCycleBounds() {
        LocalDate start = LocalDate.of(2026, 11, 10);

        assertEquals(LocalDate.of(2026, 11, 11), ReplacementCalculator.nextDueDate(start, 1));
        assertEquals(LocalDate.of(2028, 11, 9), ReplacementCalculator.nextDueDate(start, 730));
    }

    @Test
    public void rejectsCyclesOutsideRange() {
        LocalDate start = LocalDate.of(2026, 11, 10);

        assertThrows(IllegalArgumentException.class,
                () -> ReplacementCalculator.nextDueDate(start, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ReplacementCalculator.nextDueDate(start, -1));
        assertThrows(IllegalArgumentException.class,
                () -> ReplacementCalculator.nextDueDate(start, 731));
    }

    @Test
    public void daysUntilIsDueMinusToday() {
        LocalDate today = LocalDate.of(2028, 2, 28);

        assertEquals(2, ReplacementCalculator.daysUntil(LocalDate.of(2028, 3, 1), today));
        assertEquals(0, ReplacementCalculator.daysUntil(today, today));
        assertEquals(-5, ReplacementCalculator.daysUntil(LocalDate.of(2028, 2, 23), today));
    }

    @Test
    public void statusChangesAtDayBoundaries() {
        LocalDate today = LocalDate.of(2026, 11, 10);

        assertStatus(DueStatus.OVERDUE, today.minusDays(5), today);
        assertStatus(DueStatus.OVERDUE, today.minusDays(1), today);
        assertStatus(DueStatus.TODAY, today, today);
        assertStatus(DueStatus.SOON, today.plusDays(1), today);
        assertStatus(DueStatus.SOON, today.plusDays(3), today);
        assertStatus(DueStatus.OK, today.plusDays(4), today);
        assertStatus(DueStatus.OK, today.plusDays(730), today);
    }

    @Test
    public void pastStartDateIsOverdueImmediately() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        LocalDate due = ReplacementCalculator.nextDueDate(LocalDate.of(2026, 9, 1), 60);

        assertEquals(DueStatus.OVERDUE, ReplacementCalculator.status(due, today));
    }

    private static void assertStatus(DueStatus expected, LocalDate due, LocalDate today) {
        assertEquals("due " + due, expected, ReplacementCalculator.status(due, today));
    }
}
