package com.kwmobile.hometiming.ui.main;

import static org.junit.Assert.*;
import java.io.*;
import java.time.LocalDate;
import org.junit.Test;

public class ItemPresetTest {
    private static final String[] CATEGORIES = {"욕실", "주방", "기타"};

    @Test public void fillsThreeFieldsPreservesOtherInputsAndAllowsFurtherEdits() throws Exception {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        vm.state.draft = new DesignPreviewViewModel.Draft();
        DesignPreviewViewModel.Draft draft = vm.state.draft;
        draft.id = "filter";
        draft.name = "직접 쓴 이름";
        draft.cycle = "25";
        draft.cycleEdited = true;
        draft.location = "서랍";
        draft.productName = "제품";
        draft.memo = "메모";
        draft.date = LocalDate.of(2026, 11, 1);
        draft.optionalExpanded = true;
        assertTrue(vm.applyItemPreset("베개커버", "기타", 7, CATEGORIES));
        assertEquals("베개커버", draft.name);
        assertEquals("기타", draft.category);
        assertEquals("7", draft.cycle);
        assertEquals("filter", draft.id);
        assertEquals("서랍", draft.location);
        assertEquals("제품", draft.productName);
        assertEquals("메모", draft.memo);
        assertEquals(LocalDate.of(2026, 11, 1), draft.date);
        assertTrue(draft.optionalExpanded);
        assertTrue(draft.dirty);
        // Manual input after applying a preset remains the source of truth on restoration.
        draft.name = "내 베개커버";
        draft.cycle = "10";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(vm.state);
        vm.state = (DesignPreviewViewModel.State) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertEquals("내 베개커버", vm.state.draft.name);
        assertEquals("10", vm.state.draft.cycle);
        assertTrue(vm.applyItemPreset("베개커버", "기타", 7, CATEGORIES));
        assertEquals("7", vm.state.draft.cycle);
    }

    @Test public void doesNotResurrectRemovedCategoriesOrAddUnexpectedCategories() {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        assertTrue(vm.renameCategory(false, "욕실", "세면실", CATEGORIES, "전체"));
        vm.state.draft = new DesignPreviewViewModel.Draft();
        vm.state.draft.category = "세면실";
        assertTrue(vm.applyItemPreset("칫솔", "욕실", 90, CATEGORIES));
        assertEquals("세면실", vm.state.draft.category);
        assertFalse(vm.categories(false, CATEGORIES).contains("욕실"));
        assertTrue(vm.applyItemPreset("수세미", "주방", 30, CATEGORIES));
        assertEquals("주방", vm.state.draft.category);
    }

    @Test public void neverAppliesItemPresetsToChoresOrInvalidInput() {
        DesignPreviewViewModel vm = new DesignPreviewViewModel();
        assertFalse(vm.applyItemPreset("칫솔", "욕실", 90, CATEGORIES));
        vm.state.draft = new DesignPreviewViewModel.Draft();
        vm.state.draft.chore = true;
        vm.state.draft.name = "세탁";
        assertFalse(vm.applyItemPreset("칫솔", "욕실", 90, CATEGORIES));
        assertEquals("세탁", vm.state.draft.name);
        vm.state.draft.chore = false;
        assertFalse(vm.applyItemPreset("칫솔", "욕실", 0, CATEGORIES));
        assertFalse(vm.applyItemPreset("칫솔", "욕실", 731, CATEGORIES));
        assertFalse(vm.applyItemPreset("", "욕실", 90, CATEGORIES));
        assertFalse(vm.applyItemPreset(null, "욕실", 90, CATEGORIES));
        assertEquals("세탁", vm.state.draft.name);
    }
}
