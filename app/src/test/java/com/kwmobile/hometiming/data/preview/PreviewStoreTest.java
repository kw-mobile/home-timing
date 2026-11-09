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
        assertSame(original,edited);assertEquals(before,edited.history.size());
        assertEquals(start.plusDays(30),store.find("filter").due);
    }
    @Test public void weatherMoveDoesNotChangeWeeklyRecurrenceAnchor() {
        PreviewStore store=new PreviewStore();PreviewStore.Item item=store.find("bedding");int weekday=item.weekday;
        item.due=PreviewStore.today().plusDays(1);
        assertTrue(store.complete(item.id));
        assertTrue(item.due.isAfter(PreviewStore.today()));
        assertEquals(weekday,item.due.getDayOfWeek().getValue());
    }
    @Test public void newDraftCreatesUniqueItemsAndResetRestoresFixture() {
        PreviewStore store=new PreviewStore();
        PreviewStore.Item a=store.save("","용품 A","기타","",false,1,PreviewStore.today(),false,1);
        PreviewStore.Item b=store.save("","용품 B","기타","",false,730,PreviewStore.today(),false,1);
        assertNotEquals(a.id,b.id);assertEquals(PreviewStore.today().plusDays(730),b.due);
        assertTrue(store.delete(a.id));assertNull(store.find(a.id));assertNotNull(store.find(b.id));
        store.reset();assertEquals(5,store.items.size());assertNull(store.find(b.id));
    }
}
