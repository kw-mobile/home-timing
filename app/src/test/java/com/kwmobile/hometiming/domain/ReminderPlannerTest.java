package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ReminderKey.Kind;
import com.kwmobile.hometiming.domain.ReminderKey.Type;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ReminderPlannerTest {
    private static final LocalTime NINE = LocalTime.of(9, 0);

    private static Clock at(int day, int hour) {
        return Clock.fixed(LocalDateTime.of(2026, 11, day, hour, 0).atZone(DateRules.ZONE).toInstant(), ZoneOffset.UTC);
    }

    private static long seoul(int day, int hour) {
        return LocalDateTime.of(2026, 11, day, hour, 0).atZone(DateRules.ZONE).toInstant().toEpochMilli();
    }

    private static Item item(LocalDate due) {
        return new Item("filter", "정수기 필터", "ETC", due.minusDays(90), 90, due.minusDays(90), due, "occ-1", 2,
                "", "", "", "alice", false);
    }

    private static List<PlannedReminder> plan(Item item, int advanceDays, Clock clock) {
        return ReminderPlanner.plan("house-a", Collections.singletonList(item), Collections.<Chore>emptyList(), NINE,
                advanceDays, clock);
    }

    private static PlannedReminder find(List<PlannedReminder> plan, Type type) {
        for (PlannedReminder reminder : plan) {
            if (reminder.key.type == type) {
                return reminder;
            }
        }
        return null;
    }

    @Test
    public void plansAdvanceAndDueAtTheReminderTimeInSeoul() {
        List<PlannedReminder> plan = plan(item(LocalDate.of(2026, 11, 14)), 3, at(11, 8));

        assertEquals(2, plan.size());
        PlannedReminder advance = find(plan, Type.ADVANCE);
        PlannedReminder due = find(plan, Type.DUE);
        assertEquals(seoul(11, 9), advance.fireAtMillis);
        assertEquals(3, advance.daysBefore);
        assertEquals(seoul(14, 9), due.fireAtMillis);
        assertEquals(0, due.daysBefore);
        assertEquals("정수기 필터", due.name);
        assertEquals(2, due.revision);
        assertEquals(new ReminderKey(Kind.ITEM, "house-a", "filter", Type.DUE, "occ-1"), due.key);
    }

    @Test
    public void aPassedAdvanceReminderIsSkipped() {
        List<PlannedReminder> plan = plan(item(LocalDate.of(2026, 11, 14)), 3, at(11, 10));

        assertNull(find(plan, Type.ADVANCE));
        assertEquals(seoul(14, 9), find(plan, Type.DUE).fireAtMillis);
    }

    @Test
    public void zeroAdvanceDaysTurnsTheAdvanceReminderOff() {
        assertNull(find(plan(item(LocalDate.of(2026, 11, 14)), 0, at(11, 8)), Type.ADVANCE));
    }

    @Test
    public void aPassedOrOverdueDueReminderFiresNow() {
        Clock clock = at(11, 10);

        assertEquals(clock.millis(), find(plan(item(LocalDate.of(2026, 11, 11)), 3, clock), Type.DUE).fireAtMillis);
        assertEquals(clock.millis(), find(plan(item(LocalDate.of(2026, 11, 1)), 3, clock), Type.DUE).fireAtMillis);
    }

    @Test
    public void choresUseTheMovedDateAndTheirOccurrence() {
        Chore chore = new Chore("wash", "침구 세탁", "BEDDING_WASH", true, 6, LocalDate.of(2026, 11, 14),
                LocalDate.of(2026, 11, 15), "occ-7", 3, null, null, "", "", "alice");

        List<PlannedReminder> plan = ReminderPlanner.plan("house-a", Collections.<Item>emptyList(),
                Collections.singletonList(chore), NINE, 1, at(11, 8));

        PlannedReminder due = find(plan, Type.DUE);
        assertEquals(Kind.CHORE, due.key.kind);
        assertEquals("occ-7", due.key.occurrenceId);
        assertEquals(LocalDate.of(2026, 11, 15), due.dueDate);
        assertEquals(seoul(15, 9), due.fireAtMillis);
        assertEquals(seoul(14, 9), find(plan, Type.ADVANCE).fireAtMillis);
    }

    @Test
    public void settingsMatchTheRules() {
        assertEquals(LocalTime.of(7, 30), ReminderPlanner.parseTime("07:30"));
        assertNull(ReminderPlanner.parseTime("7:30"));
        assertNull(ReminderPlanner.parseTime("24:00"));
        assertNull(ReminderPlanner.parseTime(null));
        assertTrue(ReminderPlanner.isValidSettings("23:59", 0));
        assertTrue(ReminderPlanner.isValidSettings("00:00", 30));
        assertFalse(ReminderPlanner.isValidSettings("09:00", 31));
        assertFalse(ReminderPlanner.isValidSettings("09:00", -1));
        assertFalse(ReminderPlanner.isValidSettings("9시", 3));
    }
}
