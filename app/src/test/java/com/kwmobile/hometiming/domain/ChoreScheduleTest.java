package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ItemSchedule.Conflict;

import java.time.LocalDate;

import org.junit.Test;

public class ChoreScheduleTest {
    private static final int SATURDAY = 6;
    private static final LocalDate SAT_NOV_7 = LocalDate.of(2026, 11, 7);

    private static Chore saturdayChore() {
        return new Chore("wash", "침구 세탁", "BEDDING_WASH", true, SATURDAY, SAT_NOV_7, SAT_NOV_7,
                "occ-1", 1, null, null, "", "", "alice");
    }

    @Test
    public void firstDueIsTheNextSuchWeekdayAfterStart() {
        assertEquals(SAT_NOV_7, ChoreSchedule.firstDue(LocalDate.of(2026, 11, 4), SATURDAY));
        // Starting on that weekday goes to the following week, like the design preview.
        assertEquals(LocalDate.of(2026, 11, 14), ChoreSchedule.firstDue(SAT_NOV_7, SATURDAY));
    }

    private static ChoreDraft draft(String name, boolean sensitive, int weekday) {
        return new ChoreDraft(name, "BEDDING_WASH", sensitive, weekday, LocalDate.of(2026, 11, 4), "베란다", "");
    }

    @Test
    public void registerStartsOnTheFirstOccurrence() {
        Chore chore = ChoreSchedule.register("wash", draft("침구 세탁", true, SATURDAY), "alice", "occ-1");

        assertEquals(SAT_NOV_7, chore.plannedDueDate);
        assertEquals(SAT_NOV_7, chore.nextDueDate);
        assertEquals(1, chore.revision);
        assertEquals("alice", chore.createdBy);
        assertEquals("베란다", chore.location);
    }

    @Test
    public void displayEditKeepsDatesAndRevision() {
        Chore edited = ChoreSchedule.edit(saturdayChore(), draft("이불 빨래", true, SATURDAY), LocalDate.of(2026, 11, 6));

        assertEquals("이불 빨래", edited.name);
        assertEquals(SAT_NOV_7, edited.nextDueDate);
        assertEquals(1, edited.revision);
    }

    @Test
    public void weatherSwitchBumpsRevision() {
        Chore edited = ChoreSchedule.edit(saturdayChore(), draft("침구 세탁", false, SATURDAY), LocalDate.of(2026, 11, 6));

        assertEquals(2, edited.revision);
        assertEquals(SAT_NOV_7, edited.plannedDueDate);
    }

    @Test
    public void weekdayEditRestartsThisOccurrenceOnTheNewWeekday() {
        Chore edited = ChoreSchedule.edit(saturdayChore(), draft("침구 세탁", true, 7), LocalDate.of(2026, 11, 6));

        assertEquals(LocalDate.of(2026, 11, 8), edited.plannedDueDate);
        assertEquals(LocalDate.of(2026, 11, 8), edited.nextDueDate);
        assertEquals("occ-1", edited.occurrenceId);
        assertEquals(2, edited.revision);
    }

    @Test
    public void mvpExampleMovedToSundayThenCompleted() {
        Chore moved = ChoreSchedule.reschedule(saturdayChore(), LocalDate.of(2026, 11, 8));

        Chore done = ChoreSchedule.complete(moved, LocalDate.of(2026, 11, 8), "occ-2");

        assertEquals(LocalDate.of(2026, 11, 14), done.plannedDueDate);
        assertEquals(LocalDate.of(2026, 11, 14), done.nextDueDate);
    }

    @Test
    public void mvpExampleLateCompletionSkipsMissedWeeks() {
        assertEquals(LocalDate.of(2026, 11, 21),
                ChoreSchedule.nextPlanned(SAT_NOV_7, LocalDate.of(2026, 11, 20)));
    }

    @Test
    public void completingOnAWeekdayMovesPastIt() {
        // Done exactly on the next Saturday: that Saturday is not "after" it.
        assertEquals(LocalDate.of(2026, 11, 21),
                ChoreSchedule.nextPlanned(SAT_NOV_7, LocalDate.of(2026, 11, 14)));
    }

    @Test
    public void earlyCompletionStillGoesToNextWeek() {
        assertEquals(LocalDate.of(2026, 11, 14),
                ChoreSchedule.nextPlanned(SAT_NOV_7, LocalDate.of(2026, 11, 5)));
    }

    @Test
    public void completionStartsANewOccurrence() {
        Chore dismissed = ChoreSchedule.dismissSuggestion(saturdayChore());

        Chore done = ChoreSchedule.complete(dismissed, SAT_NOV_7, "occ-2");

        assertEquals("occ-2", done.occurrenceId);
        assertEquals(dismissed.revision + 1, done.revision);
        assertEquals(SAT_NOV_7, done.lastDoneDate);
        assertNull(done.dismissedOccurrenceId);
        assertFalse(done.isSuggestionDismissed());
        assertThrows(IllegalArgumentException.class,
                () -> ChoreSchedule.complete(saturdayChore(), SAT_NOV_7, "occ-1"));
    }

    @Test
    public void reschedulingKeepsOccurrenceAndOriginalDate() {
        Chore moved = ChoreSchedule.reschedule(saturdayChore(), LocalDate.of(2026, 11, 8));

        assertEquals(LocalDate.of(2026, 11, 8), moved.nextDueDate);
        assertEquals(SAT_NOV_7, moved.plannedDueDate);
        assertEquals("occ-1", moved.occurrenceId);
        assertEquals(2, moved.revision);
    }

    @Test
    public void reschedulingToTheSameDateChangesNothing() {
        Chore chore = saturdayChore();

        assertSame(chore, ChoreSchedule.reschedule(chore, SAT_NOV_7));
    }

    @Test
    public void canOnlyMoveToTodayOrLater() {
        LocalDate today = LocalDate.of(2026, 11, 6);

        assertTrue(ChoreSchedule.canReschedule(today, today));
        assertFalse(ChoreSchedule.canReschedule(today.minusDays(1), today));
        assertFalse(ChoreSchedule.canReschedule(null, today));
    }

    @Test
    public void dismissingIsPerOccurrenceAndIdempotent() {
        Chore dismissed = ChoreSchedule.dismissSuggestion(saturdayChore());

        assertTrue(dismissed.isSuggestionDismissed());
        assertEquals(2, dismissed.revision);
        assertSame(dismissed, ChoreSchedule.dismissSuggestion(dismissed));
    }

    @Test
    public void conflictTellsCompletionFromReschedule() {
        Chore shown = saturdayChore();
        Chore completed = ChoreSchedule.complete(shown, SAT_NOV_7, "occ-2");
        Chore moved = ChoreSchedule.reschedule(shown, LocalDate.of(2026, 11, 8));

        assertEquals(Conflict.NONE, ChoreSchedule.conflict(shown, "occ-1", 1));
        assertEquals(Conflict.ALREADY_COMPLETED, ChoreSchedule.conflict(completed, "occ-1", 1));
        assertEquals(Conflict.SCHEDULE_CHANGED, ChoreSchedule.conflict(moved, "occ-1", 1));
    }
}
