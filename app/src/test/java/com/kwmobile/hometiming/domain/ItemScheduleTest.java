package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ItemSchedule.Conflict;

import java.time.LocalDate;

import org.junit.Test;

public class ItemScheduleTest {
    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    private static ItemDraft draft(String name, LocalDate start, int cycle) {
        return new ItemDraft(name, "SHOWER_FILTER", start, cycle, "", "욕실", "");
    }

    private static Item registered() {
        return ItemSchedule.register("filter", draft("샤워기 필터", NOV_1, 60), "alice", "occ-1");
    }

    @Test
    public void registerStartsFromStartDate() {
        Item item = registered();

        assertEquals(NOV_1, item.lastReplacedDate);
        assertEquals(LocalDate.of(2026, 12, 31), item.nextDueDate);
        assertEquals(1, item.revision);
        assertEquals("occ-1", item.occurrenceId);
        assertEquals("alice", item.createdBy);
        assertEquals("욕실", item.location);
    }

    @Test
    public void displayOnlyEditKeepsRevision() {
        Item edited = ItemSchedule.edit(registered(), new ItemDraft("욕실 샤워기 필터", "SHOWER_FILTER", NOV_1, 60,
                "ACME 필터", "욕실", "서랍 둘째 칸"));

        assertEquals(1, edited.revision);
        assertEquals("욕실 샤워기 필터", edited.name);
        assertEquals("서랍 둘째 칸", edited.memo);
        assertEquals("occ-1", edited.occurrenceId);
    }

    @Test
    public void cycleEditRecomputesDueAndBumpsRevision() {
        Item edited = ItemSchedule.edit(registered(), draft("샤워기 필터", NOV_1, 90));

        assertEquals(LocalDate.of(2027, 1, 30), edited.nextDueDate);
        assertEquals(2, edited.revision);
        assertEquals("occ-1", edited.occurrenceId);
    }

    @Test
    public void startDateEditMovesLastReplacementUntilFirstCompletion() {
        Item edited = ItemSchedule.edit(registered(), draft("샤워기 필터", LocalDate.of(2026, 10, 25), 60));

        assertEquals(LocalDate.of(2026, 10, 25), edited.lastReplacedDate);
        assertEquals(LocalDate.of(2026, 12, 24), edited.nextDueDate);
        assertEquals(2, edited.revision);
    }

    @Test
    public void startDateEditAfterCompletionKeepsLastReplacement() {
        Item completed = ItemSchedule.complete(registered(), LocalDate.of(2026, 11, 20), "occ-2");

        Item edited = ItemSchedule.edit(completed, draft("샤워기 필터", LocalDate.of(2026, 10, 25), 60));
        assertEquals(LocalDate.of(2026, 11, 20), edited.lastReplacedDate);

        assertTrue(ItemSchedule.startDateFits(completed, LocalDate.of(2026, 11, 20)));
        assertFalse(ItemSchedule.startDateFits(completed, LocalDate.of(2026, 11, 21)));
        assertThrows(IllegalArgumentException.class,
                () -> ItemSchedule.edit(completed, draft("샤워기 필터", LocalDate.of(2026, 11, 21), 60)));
    }

    @Test
    public void completionCountsFromActualDateWithNewOccurrence() {
        Item item = registered();

        // Due 12/31, actually replaced late on 1/3: next cycle starts 1/3.
        Item completed = ItemSchedule.complete(item, LocalDate.of(2027, 1, 3), "occ-2");

        assertEquals(LocalDate.of(2027, 1, 3), completed.lastReplacedDate);
        assertEquals(LocalDate.of(2027, 3, 4), completed.nextDueDate);
        assertEquals("occ-2", completed.occurrenceId);
        assertEquals(2, completed.revision);
        assertEquals(NOV_1, completed.startDate);
    }

    @Test
    public void completionRejectsEarlierDateOrSameOccurrence() {
        Item item = registered();

        assertThrows(IllegalArgumentException.class,
                () -> ItemSchedule.complete(item, NOV_1.minusDays(1), "occ-2"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemSchedule.complete(item, NOV_1.plusDays(5), "occ-1"));
        // Same day as the previous replacement is allowed (#20).
        assertEquals(NOV_1, ItemSchedule.complete(item, NOV_1, "occ-2").lastReplacedDate);
    }

    @Test
    public void historyRecordsTheFinishedOccurrence() {
        HistoryEntry entry = ItemSchedule.historyOf(registered(), LocalDate.of(2027, 1, 3), "bob", "밥");

        assertEquals("occ-1", entry.occurrenceId);
        assertEquals(LocalDate.of(2026, 12, 31), entry.previousDueDate);
        assertEquals(60, entry.cycleDaysUsed);
        assertEquals("밥", entry.completedByName);
        assertEquals(3, entry.daysLate());
    }

    @Test
    public void conflictTellsCompletionFromReschedule() {
        Item shown = registered();
        Item completedElsewhere = ItemSchedule.complete(shown, LocalDate.of(2026, 11, 20), "occ-2");
        Item rescheduledElsewhere = ItemSchedule.edit(shown, draft("샤워기 필터", NOV_1, 90));

        assertEquals(Conflict.NONE, ItemSchedule.conflict(shown, shown.occurrenceId, shown.revision));
        assertEquals(Conflict.ALREADY_COMPLETED,
                ItemSchedule.conflict(completedElsewhere, shown.occurrenceId, shown.revision));
        assertEquals(Conflict.SCHEDULE_CHANGED,
                ItemSchedule.conflict(rescheduledElsewhere, shown.occurrenceId, shown.revision));
    }

    @Test
    public void registrantOrOwnerMayDelete() {
        Item item = registered();

        assertTrue(item.canDelete("alice", "owner"));
        assertTrue(item.canDelete("owner", "owner"));
        assertFalse(item.canDelete("bob", "owner"));
        assertFalse(item.canDelete("bob", null));
    }
}
