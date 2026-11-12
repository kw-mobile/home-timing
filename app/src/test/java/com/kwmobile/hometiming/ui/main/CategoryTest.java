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
