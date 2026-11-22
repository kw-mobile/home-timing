package com.kwmobile.hometiming.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class CategoryCatalogTest {
    @Test public void oldCategoryIdsFollowRenamesAndSuccessiveDeletes() {
        String bathroom = CategoryCatalog.legacyId(false, "욕실");
        String kitchen = CategoryCatalog.legacyId(false, "주방");
        String other = CategoryCatalog.legacyId(false, "ETC");
        CategoryCatalog renamed = CategoryCatalog.defaults(false).rename(bathroom, "화장실");
        assertEquals("화장실", renamed.label(bathroom, null));
        CategoryCatalog moved = renamed.delete(bathroom, kitchen).delete(kitchen, other);
        assertEquals("기타", moved.label(bathroom, null));
        assertEquals(other, moved.resolve(kitchen));
        assertNull(moved.idForName("화장실"));
        assertThrows(IllegalArgumentException.class, () -> moved.delete(other, bathroom));
    }

    @Test public void aLegacyLabelCanAliasAnExistingCustomCategoryWithoutDuplicatingIt() {
        String id = "c_00000000000000000000000000000001";
        String old = CategoryCatalog.legacyId(false, "서재");
        CategoryCatalog original = CategoryCatalog.defaults(false).add(id, " 서재 ");
        CategoryCatalog adopted = original.alias(old, id);
        assertEquals(original.labels, adopted.labels);
        assertEquals(id, adopted.resolve(old));
        assertThrows(IllegalArgumentException.class, () -> adopted.add(old, "창고"));
    }

    @Test public void assigningAChoreDisplayCategoryDoesNotChangeItsScheduleOrType() {
        java.time.LocalDate date = java.time.LocalDate.of(2026, 11, 22);
        ChoreDraft draft = new ChoreDraft("이불 빨래", ChoreDraft.TYPE_BEDDING_WASH, true, 1,
                date, "", "", "default_bedding", "빨래");
        Chore created = ChoreSchedule.register("chore", draft, "alice", "first");
        Chore done = ChoreSchedule.complete(created, date.plusDays(1), "second");
        assertEquals(ChoreDraft.TYPE_BEDDING_WASH, done.type);
        assertEquals("default_bedding", done.categoryId);
        assertEquals("빨래", done.category);
        assertEquals(date.plusDays(8), done.nextDueDate);
    }
}
