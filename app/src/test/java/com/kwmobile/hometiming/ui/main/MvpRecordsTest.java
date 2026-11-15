package com.kwmobile.hometiming.ui.main;

import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.domain.*;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class MvpRecordsTest {
    private final LocalDate today = LocalDate.of(2026, 11, 14);

    @Test public void kindsWithSameDocumentIdKeepDistinctRoutesAndWriteSnapshots() {
        Item item = ItemSchedule.register("same-id", new ItemDraft("칫솔", "욕실", today.minusDays(30), 30,
                "제품", "위치", "메모"), "alice", "item-occurrence");
        Chore chore = ChoreSchedule.register("same-id", new ChoreDraft("침구", ChoreDraft.TYPE_BEDDING_WASH,
                true, 5, today.minusDays(7), "침실", "세탁"), "bob", "chore-occurrence");
        PreviewStore.Item a = MvpRecords.item(item), b = MvpRecords.chore(chore, today);
        assertEquals("item:same-id", a.id); assertEquals("chore:same-id", b.id);
        assertSame(item, a.serverItem); assertSame(chore, b.serverChore);
        assertNull(a.serverChore); assertNull(b.serverItem);
        assertEquals("alice", a.createdBy); assertEquals("bob", b.createdBy);
        assertEquals("제품", a.productName); assertEquals("메모", a.memo);
    }

    @Test public void completionDoesNotTurnOriginalRegistrationDateIntoNewStartDate() {
        Item item = ItemSchedule.register("brush", new ItemDraft("칫솔", "", today.minusDays(90), 90,
                "", "", ""), "alice", "one");
        Item completed = ItemSchedule.complete(item, today.minusDays(1), "two");
        PreviewStore.Item row = MvpRecords.item(completed);
        assertEquals(today.plusDays(89), row.due);
        assertEquals(today.minusDays(1), row.completedOn);
        assertEquals(today.minusDays(90), row.serverItem.startDate);
        assertEquals("기타", row.category);
        assertTrue(row.history.isEmpty()); // History must come from its own subscription, never a fixture.
    }

    @Test public void movedAndDismissedChorePreservesOriginalWeekAndOccurrence() {
        Chore original = new Chore("c", "세탁", ChoreDraft.TYPE_BEDDING_WASH, true, 5,
                today.minusDays(1), today.plusDays(1), "round", 4, "round", today.minusDays(7),
                "침실", "메모", "alice");
        PreviewStore.Item row = MvpRecords.chore(original, today);
        assertEquals(today.plusDays(1), row.due);
        assertEquals(today.minusDays(1), row.plannedDue);
        assertEquals(5, row.weekday);
        assertTrue(row.proposalDismissed);
        assertEquals(4, row.revision);
        Chore next = ChoreSchedule.complete(row.serverChore, today, "next");
        assertEquals(LocalDate.of(2026, 11, 20), next.plannedDueDate);
        assertFalse(MvpRecords.chore(next, today).proposalDismissed);
    }

    @Test public void movingAnUncompletedChoreDoesNotPretendItWasCompletedOrRaiseTheCompletionDate() {
        LocalDate now = PreviewStore.today();
        Chore chore = new Chore("c", "세탁", ChoreDraft.TYPE_BEDDING_WASH, true, 5,
                now, now.plusDays(2), "one", 2, null, null, "", "", "alice");
        PreviewStore.Item row = MvpRecords.chore(chore, now);
        row.history.add(new PreviewStore.History(now.plusDays(2), "Alice · 일정 변경", false));
        PreviewStore store = new PreviewStore();
        assertFalse(store.completedToday(row));
        assertEquals(now, store.earliestCompletion(row));
    }

    @Test public void everySupportedChoreTypeSurvivesEditing() {
        for (String type : new String[]{ChoreDraft.TYPE_BEDDING_WASH, ChoreDraft.TYPE_OUTDOOR_DRY,
                ChoreDraft.TYPE_VENTILATION, ChoreDraft.TYPE_CLEANING, ChoreDraft.TYPE_ETC})
            assertEquals(type, MvpRecords.choreType(MvpRecords.category(type)));
    }
}
