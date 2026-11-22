package com.kwmobile.hometiming.domain;

import org.junit.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class WidgetScheduleTest {
    private final LocalDate today = LocalDate.of(2026, 11, 22);
    private WidgetSchedule.Entry entry(String id, int offset) {
        return new WidgetSchedule.Entry(ReminderKey.Kind.ITEM, id, id, today.plusDays(offset));
    }
    @Test public void includesOverdueThroughSevenDaysAndExcludesDayEight() {
        List<WidgetSchedule.Entry> all = Arrays.asList(entry("outside", 8), entry("day-seven", 7),
                entry("day-two", 2), entry("today", 0), entry("older", -5), entry("yesterday", -1));
        List<WidgetSchedule.Entry> rows = WidgetSchedule.upcoming(all, today);
        assertEquals(5, rows.size());
        assertEquals(Arrays.asList("older", "yesterday", "today", "day-two", "day-seven"),
                Arrays.asList(rows.get(0).id, rows.get(1).id, rows.get(2).id, rows.get(3).id, rows.get(4).id));
        assertEquals("outside", all.get(0).id);
    }
    @Test public void formatsOverdueTodayAndFutureWithoutReversingSigns() {
        assertEquals("D+3", WidgetSchedule.dDay(entry("overdue", -3), today));
        assertEquals("D-day", WidgetSchedule.dDay(entry("today", 0), today));
        assertEquals("D-2", WidgetSchedule.dDay(entry("future", 2), today));
        assertEquals("D-7", WidgetSchedule.dDay(entry("boundary", 7), today));
    }
    @Test public void crossesSeoulMidnightRegardlessOfDeviceZone() {
        Clock before = Clock.fixed(Instant.parse("2026-11-22T14:59:59Z"), ZoneOffset.ofHours(-8));
        Clock after = Clock.offset(before, java.time.Duration.ofSeconds(1));
        assertEquals(today, WidgetSchedule.today(before));
        assertEquals(today.plusDays(1), WidgetSchedule.today(after));
        WidgetSchedule.Entry tomorrow = entry("rollover", 1);
        assertEquals("D-1", WidgetSchedule.dDay(tomorrow, WidgetSchedule.today(before)));
        assertEquals("D-day", WidgetSchedule.dDay(tomorrow, WidgetSchedule.today(after)));
        assertEquals("D+1", WidgetSchedule.dDay(tomorrow, today.plusDays(2)));
        assertTrue(WidgetSchedule.upcoming(Collections.singletonList(entry("outside", 8)), today).isEmpty());
        assertEquals(1, WidgetSchedule.upcoming(Collections.singletonList(entry("outside", 8)), today.plusDays(1)).size());
    }
    @Test public void usesMovedExecutionDateAndNextOccurrenceAfterCompletion() {
        Item completed = new Item("same-id", "수세미", "주방", today.minusDays(30), 30, today,
                today.plusDays(30), "new-occurrence", 2, "", "", "", "owner", true);
        Chore moved = new Chore("same-id", "세탁", "BEDDING_WASH", true, 7, today, today.plusDays(2),
                "chore-occurrence", 1, null, null, "", "", "owner");
        List<WidgetSchedule.Entry> rows = WidgetSchedule.project(Collections.singletonList(completed), Collections.singletonList(moved));
        List<WidgetSchedule.Entry> upcoming = WidgetSchedule.upcoming(rows, today);
        assertEquals(1, upcoming.size());
        assertEquals(ReminderKey.Kind.CHORE, upcoming.get(0).kind);
        assertEquals("same-id", upcoming.get(0).id);
        assertEquals("D-2", WidgetSchedule.dDay(upcoming.get(0), today));
        assertEquals("BEDDING_WASH", upcoming.get(0).category);
        assertEquals("주방", rows.get(0).category);
    }
    @Test public void yearBoundaryAndEmptyList() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        WidgetSchedule.Entry row = new WidgetSchedule.Entry(ReminderKey.Kind.CHORE, "new-year", "청소", LocalDate.of(2027, 1, 7));
        assertEquals(1, WidgetSchedule.upcoming(Collections.singletonList(row), end).size());
        assertEquals("D-7", WidgetSchedule.dDay(row, end));
        assertTrue(WidgetSchedule.upcoming(Collections.emptyList(), end).isEmpty());
    }
}
