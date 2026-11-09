package com.kwmobile.hometiming.ui.main;
import org.junit.Test;
import static org.junit.Assert.*;
public class DesignPreviewViewModelTest {
    @Test public void detailReturnsToOriginCalendarWithoutCopyingItem() {
        DesignPreviewViewModel vm=new DesignPreviewViewModel();
        vm.tab("calendar");vm.navigate("detail","filter");
        assertEquals("filter",vm.state.route.id);assertTrue(vm.back());
        assertEquals("calendar",vm.state.route.screen);
    }
    @Test public void tabsClearSubpageHistoryAndDraftIsSeparateFromStoredItem() {
        DesignPreviewViewModel vm=new DesignPreviewViewModel();
        vm.tab("items");vm.edit("filter");vm.state.draft.name="수정 중";
        assertEquals("샤워기 필터",vm.state.store.find("filter").name);
        vm.tab("settings");assertTrue(vm.state.back.isEmpty());
        assertTrue(vm.back());assertEquals("home",vm.state.route.screen);
        assertFalse(vm.back());
    }
}
