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
    @Test public void includesOverdueBeforeTodayAndOnlyTomorrowInSecondSection() {
        List<WidgetSchedule.Entry> all = Arrays.asList(entry("future", 2), entry("tomorrow", 1),
                entry("today", 0), entry("older", -5), entry("yesterday", -1));
        List<WidgetSchedule.Entry> first = WidgetSchedule.day(all, today, false);
        assertEquals(Arrays.asList("older", "yesterday", "today"),
                Arrays.asList(first.get(0).id, first.get(1).id, first.get(2).id));
        assertEquals(1, WidgetSchedule.day(all, today, true).size());
        assertEquals("tomorrow", WidgetSchedule.day(all, today, true).get(0).id);
        assertEquals("future", all.get(0).id); // Projection does not reorder the source.
    }
    @Test public void rollsTomorrowIntoTodayAtSeoulMidnightRegardlessOfDeviceZone() {
        Clock before = Clock.fixed(Instant.parse("2026-11-22T14:59:59Z"), ZoneOffset.ofHours(-8));
        Clock after = Clock.offset(before, java.time.Duration.ofSeconds(1));
        assertEquals(today, WidgetSchedule.today(before));
        assertEquals(today.plusDays(1), WidgetSchedule.today(after));
        List<WidgetSchedule.Entry> rows = Collections.singletonList(entry("rollover", 1));
        assertTrue(WidgetSchedule.day(rows, WidgetSchedule.today(before), false).isEmpty());
        assertEquals(1, WidgetSchedule.day(rows, WidgetSchedule.today(after), false).size());
        assertTrue(WidgetSchedule.day(rows, WidgetSchedule.today(after), true).isEmpty());
    }
    @Test public void usesMovedExecutionDateAndNextOccurrenceAfterCompletion() {
        Item completed = new Item("same-id", "수세미", "주방", today.minusDays(30), 30, today,
                today.plusDays(30), "new-occurrence", 2, "", "", "", "owner", true);
        Chore moved = new Chore("same-id", "세탁", "laundry", true, 7, today, today.plusDays(1),
                "chore-occurrence", 1, null, null, "", "", "owner");
        List<WidgetSchedule.Entry> rows = WidgetSchedule.project(Collections.singletonList(completed), Collections.singletonList(moved));
        assertTrue(WidgetSchedule.day(rows, today, false).isEmpty());
        List<WidgetSchedule.Entry> tomorrow = WidgetSchedule.day(rows, today, true);
        assertEquals(1, tomorrow.size());
        assertEquals(ReminderKey.Kind.CHORE, tomorrow.get(0).kind);
        assertEquals("same-id", tomorrow.get(0).id);
        assertEquals("세탁", tomorrow.get(0).name);
    }
    @Test public void yearBoundaryAndEmptySections() {
        LocalDate end = LocalDate.of(2026, 12, 31);
        List<WidgetSchedule.Entry> rows = Collections.singletonList(new WidgetSchedule.Entry(
                ReminderKey.Kind.CHORE, "new-year", "청소", LocalDate.of(2027, 1, 1)));
        assertEquals(1, WidgetSchedule.day(rows, end, true).size());
        assertTrue(WidgetSchedule.day(Collections.emptyList(), end, false).isEmpty());
    }
}
