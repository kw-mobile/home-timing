package com.kwmobile.hometiming.data.preview;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;

public class PreviewStoreTest {
    @Test public void completionCannotDuplicateAndAdvancesFromActualCompletion() {
        PreviewStore store=new PreviewStore();
        PreviewStore.Item item=store.find("brush");
        int before=item.history.size();
        assertTrue(store.complete(item.id));
        assertEquals(PreviewStore.today().plusDays(90),item.due);
        assertEquals(PreviewStore.today(),item.history.get(0).date);
        assertFalse(store.complete(item.id));
        assertEquals(before+1,item.history.size());
    }
    @Test public void editingPreservesIdentityAndHistoryAcrossRoutes() {
        PreviewStore store=new PreviewStore();PreviewStore.Item original=store.find("filter");
        int before=original.history.size();
        LocalDate start=PreviewStore.today().minusDays(20);
        PreviewStore.Item edited=store.save("filter","새 필터","욕실","욕실",false,30,start,false,start.getDayOfWeek().getValue());
        assertSame(original,edited);assertEquals(before,edited.history.size());assertEquals("가족",edited.createdBy);
        assertEquals(start.plusDays(30),store.find("filter").due);
    }
    @Test public void weatherMoveDoesNotChangeWeeklyRecurrenceAnchor() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("bedding");int weekday=item.weekday;LocalDate originalPlan=item.plannedDue;
        item.due=PreviewStore.today().plusDays(1);
        assertTrue(store.complete(item.id));
        assertTrue(item.due.isAfter(PreviewStore.today()));
        assertEquals(weekday,item.due.getDayOfWeek().getValue());
        assertEquals(originalPlan.plusWeeks(1),item.due);
    }
    @Test public void newDraftCreatesUniqueItemsAndResetRestoresFixture() {
        PreviewStore store=new PreviewStore();
        PreviewStore.Item a=store.save("","용품 A","기타","",false,1,PreviewStore.today(),false,1);
        PreviewStore.Item b=store.save("","용품 B","기타","",false,730,PreviewStore.today(),false,1);
        assertNotEquals(a.id,b.id);assertEquals(PreviewStore.today().plusDays(730),b.due);
        assertTrue(store.delete(a.id));assertNull(store.find(a.id));assertNotNull(store.find(b.id));
        store.reset();assertEquals(5,store.items.size());assertNull(store.find(b.id));
    }
    @Test public void weatherSuggestionCannotMoveSuppliesOrUnsuitableChores() {
        PreviewStore store=new PreviewStore();
        PreviewStore.Item supply=store.find("filter");LocalDate original=supply.due;
        assertFalse(store.moveWeatherSuggestion(supply.id,PreviewStore.today().plusDays(1)));
        assertEquals(original,supply.due);
        PreviewStore.Item chore=store.find("bedding");chore.weatherSensitive=false;
        assertFalse(store.moveWeatherSuggestion(chore.id,PreviewStore.today().plusDays(1)));
        chore.weatherSensitive=true;
        assertFalse(store.moveWeatherSuggestion(chore.id,PreviewStore.today().minusDays(1)));
        int anchor=chore.weekday;
        assertTrue(store.moveWeatherSuggestion(chore.id,PreviewStore.today().plusDays(1)));
        assertFalse(store.moveWeatherSuggestion(chore.id,PreviewStore.today().plusDays(1)));
        assertEquals(anchor,chore.weekday);
    }
    @Test public void keepingThisSuggestionDoesNotSuppressTheNextOccurrence() {
        PreviewStore store=new PreviewStore();PreviewStore.Item chore=store.find("bedding");
        LocalDate original=chore.due;int anchor=chore.weekday;
        assertTrue(store.dismissWeatherSuggestion(chore.id));
        assertFalse(store.moveWeatherSuggestion(chore.id,PreviewStore.today().plusDays(1)));
        assertEquals(original,chore.due);
        assertTrue(store.complete(chore.id));assertFalse(chore.proposalDismissed);
        assertEquals(anchor,chore.due.getDayOfWeek().getValue());
        assertTrue(store.moveWeatherSuggestion(chore.id,PreviewStore.today().plusDays(1)));
    }
    @Test public void lateCompletionSkipsElapsedWeeklyOccurrences() {
        PreviewStore store=new PreviewStore();LocalDate today=PreviewStore.today();
        LocalDate original=today.minusDays(18);
        PreviewStore.Item item=new PreviewStore.Item("late","늦은 집안일","청소","",true,7,original.minusWeeks(1),original);
        store.items.add(item);
        LocalDate expected=original.plusWeeks(1);
        while(!expected.isAfter(today))expected=expected.plusWeeks(1);
        assertTrue(store.complete(item.id));assertEquals(expected,item.due);assertEquals(expected,item.plannedDue);
    }
    @Test public void metadataEditsKeepMovedAndCompletedOccurrenceDates() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("bedding");
        LocalDate original=item.plannedDue;LocalDate moved=PreviewStore.today().plusDays(1);
        assertTrue(store.moveWeatherSuggestion(item.id,moved));
        store.save(item.id,"이름만 수정",item.category,item.location,true,7,item.start,item.weatherSensitive,item.weekday);
        assertEquals(moved,item.due);assertEquals(original,item.plannedDue);assertEquals(moved,store.previewDue(item.id,true,7,item.start,item.weekday));
        assertTrue(store.dismissWeatherSuggestion(item.id));
        store.save(item.id,"다시 이름 수정",item.category,item.location,true,7,item.start,item.weatherSensitive,item.weekday);
        assertTrue(item.proposalDismissed);
        assertTrue(store.complete(item.id));LocalDate next=original.plusWeeks(1);
        store.save(item.id,"완료 뒤 이름 수정",item.category,item.location,true,7,item.start,item.weatherSensitive,item.weekday);
        assertEquals(next,item.due);assertEquals(next,item.plannedDue);assertEquals(next,store.previewDue(item.id,true,7,item.start,item.weekday));
    }
    @Test public void actualPastCompletionUsesThatDateAndUndoRestoresEveryScheduleField() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("filter");
        LocalDate start=item.start,due=item.due,planned=item.plannedDue;
        int count=item.history.size();LocalDate actual=PreviewStore.today().minusDays(3);
        PreviewStore.CompletionReceipt receipt=store.complete(item.id,actual);
        assertNotNull(receipt);assertEquals(actual,item.history.get(0).date);
        assertEquals(actual.plusDays(60),item.due);assertTrue(store.completedToday(item));
        assertNull(store.complete(item.id,actual.minusDays(1)));
        assertTrue(store.undo(receipt));assertEquals(start,item.start);assertEquals(due,item.due);
        assertEquals(planned,item.plannedDue);assertEquals(count,item.history.size());
        assertFalse(store.completedToday(item));assertFalse(store.undo(receipt));
    }
    @Test public void invalidCompletionCannotChangeHistoryAndStaleUndoCannotEraseLaterEdits() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("brush");int count=item.history.size();
        assertNull(store.complete(item.id,PreviewStore.today().plusDays(1)));
        assertNull(store.complete(item.id,item.start.minusDays(1)));assertEquals(count,item.history.size());
        PreviewStore.CompletionReceipt receipt=store.complete(item.id,PreviewStore.today());assertNotNull(receipt);
        store.save(item.id,"이름 수정",item.category,item.location,false,item.cycle,item.start,false,item.weekday);
        assertFalse(store.undo(receipt));assertEquals("이름 수정",item.name);assertEquals(count+1,item.history.size());
        store.reset();assertFalse(store.undo(receipt));
    }
    @Test public void homeAndWeatherProposalUseTheSameFixtureAvailabilityContract() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("bedding");
        assertEquals(PreviewStore.today().plusDays(1),store.proposalTarget(item,true,true));
        assertNull(store.proposalTarget(item,false,true));assertNull(store.proposalTarget(item,true,false));
        assertNull(store.proposalTarget(store.find("filter"),true,true));
        assertTrue(store.dismissWeatherSuggestion(item.id));assertNull(store.proposalTarget(item,true,true));
        item.proposalDismissed=false;item.due=PreviewStore.today().plusDays(3);
        assertNull(store.proposalTarget(item,true,true));
    }
}
