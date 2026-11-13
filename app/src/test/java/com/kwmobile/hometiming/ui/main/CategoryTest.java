package com.kwmobile.hometiming.ui.main;

import static org.junit.Assert.*;
import java.io.*;
import java.util.Arrays;
import org.junit.Test;

public class CategoryTest {
    private static final String[] DEFAULTS = {"욕실", "주방", "기타"};

    @Test public void rejectsBlankReservedDuplicateAndOverlongNames() {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        assertFalse(vm.addCategory(false, "  ", DEFAULTS, "전체"));
        assertFalse(vm.addCategory(false, "전체", DEFAULTS, "전체"));
        assertFalse(vm.addCategory(false, " 욕실 ", DEFAULTS, "전체"));
        assertFalse(vm.addCategory(false, "가".repeat(31), DEFAULTS, "전체"));
        assertTrue(vm.addCategory(false, "가".repeat(30), DEFAULTS, "전체"));
        assertTrue(vm.addCategory(false, " 침실 ", DEFAULTS, "전체"));
        assertFalse(vm.addCategory(false, "침실", DEFAULTS, "전체"));
    }

    @Test public void derivesCategoriesFromItemsWithoutMixingChoresOrDuplicates() {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        vm.state.store.find("filter").category = "서재";
        vm.state.store.find("brush").category = "서재";
        vm.state.store.find("bedding").category = "베란다";
        assertEquals(Arrays.asList("욕실", "주방", "기타", "서재"),
                vm.categories(false, DEFAULTS));
        vm.state.store.delete("filter");
        vm.state.store.delete("brush");
        assertFalse(vm.categories(false, DEFAULTS).contains("서재"));
    }

    private void restoreState(DesignPreviewViewModel vm) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(vm.state);
        vm.state = (DesignPreviewViewModel.State) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
    }

    @Test public void renameDefaultUpdatesReferencesAndSurvivesRestore() throws Exception {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        vm.state.filter = "욕실";
        vm.state.draft = new DesignPreviewViewModel.Draft();
        vm.state.draft.category = "욕실";
        vm.state.draft.name = "작성 중";
        vm.state.componentDraft.category = "욕실";
        int historySize = vm.state.store.find("filter").history.size();
        assertTrue(vm.renameCategory(false, "욕실", " 세면실 ", DEFAULTS, "전체"));
        assertEquals("세면실", vm.state.store.find("filter").category);
        assertEquals("세면실", vm.state.draft.category);
        assertEquals("작성 중", vm.state.draft.name);
        assertEquals("세면실", vm.state.componentDraft.category);
        assertEquals("세면실", vm.state.filter);
        assertEquals(historySize, vm.state.store.find("filter").history.size());
        restoreState(vm);
        assertFalse(vm.categories(false, DEFAULTS).contains("욕실"));
        assertTrue(vm.categories(false, DEFAULTS).contains("세면실"));
    }

    @Test public void deleteUsedDefaultRequiresDestinationAndPreservesItems() throws Exception {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        vm.state.filter = "욕실";
        vm.state.draft = new DesignPreviewViewModel.Draft();
        vm.state.draft.category = "욕실";
        int count = vm.state.store.items.size();
        assertFalse(vm.deleteCategory(false, "욕실", null, DEFAULTS, "전체"));
        assertFalse(vm.deleteCategory(false, "욕실", "없는 분류", DEFAULTS, "전체"));
        assertFalse(vm.deleteCategory(false, "욕실", "욕실", DEFAULTS, "전체"));
        assertEquals("욕실", vm.state.store.find("filter").category);
        assertTrue(vm.deleteCategory(false, "욕실", "주방", DEFAULTS, "전체"));
        assertEquals("주방", vm.state.store.find("filter").category);
        assertEquals("주방", vm.state.draft.category);
        assertEquals("전체", vm.state.filter);
        assertEquals(count, vm.state.store.items.size());
        restoreState(vm);
        assertFalse(vm.categories(false, DEFAULTS).contains("욕실"));
        assertTrue(vm.addCategory(false, "욕실", DEFAULTS, "전체"));
    }

    @Test public void lastCategoryCanBeRenamedAndNewDraftUsesRemainingCategory() {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        assertTrue(vm.deleteCategory(false, "욕실", "주방", DEFAULTS, "전체"));
        assertTrue(vm.deleteCategory(false, "기타", "주방", DEFAULTS, "전체"));
        assertFalse(vm.deleteCategory(false, "주방", null, DEFAULTS, "전체"));
        assertTrue(vm.renameCategory(false, "주방", "집", DEFAULTS, "전체"));
        assertEquals("집", vm.defaultCategory(false, DEFAULTS, "기타"));
        assertTrue(vm.addCategory(false, "새 분류", DEFAULTS, "전체"));
        assertTrue(vm.deleteCategory(false, "집", "새 분류", DEFAULTS, "전체"));
        assertEquals("새 분류", vm.defaultCategory(false, DEFAULTS, "기타"));
    }

    @Test public void invalidEditsAndOtherKindArePreservedIncludingOldSavedStates()
            throws Exception {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        vm.state.removedItemCategories = null;
        vm.state.removedChoreCategories = null;
        restoreState(vm);
        for (String name : new String[] {"", " ", "전체", "주방", "가".repeat(31)}) {
            assertFalse(vm.renameCategory(false, "욕실", name, DEFAULTS, "전체"));
        }
        assertFalse(vm.renameCategory(false, "욕실", null, DEFAULTS, "전체"));
        assertFalse(vm.deleteCategory(false, null, null, DEFAULTS, "전체"));
        assertEquals("욕실", vm.state.store.find("filter").category);
        assertTrue(vm.addCategory(true, "욕실", new String[] {"세탁", "청소", "기타"}, "전체"));
        vm.state.store.find("bedding").category = "욕실";
        assertTrue(vm.renameCategory(false, "욕실", "가".repeat(30), DEFAULTS, "전체"));
        assertEquals("욕실", vm.state.store.find("bedding").category);
        assertTrue(vm.categories(true, new String[] {"세탁", "청소", "기타"}).contains("욕실"));
    }

    @Test public void customCategoriesSurviveSavedStateSerialization() throws Exception {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        assertTrue(vm.addCategory(false, "침실", DEFAULTS, "전체"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(vm.state);
        vm.state = (DesignPreviewViewModel.State) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertTrue(vm.categories(false, DEFAULTS).contains("침실"));
        assertFalse(vm.categories(true, DEFAULTS).contains("침실"));
    }
}
