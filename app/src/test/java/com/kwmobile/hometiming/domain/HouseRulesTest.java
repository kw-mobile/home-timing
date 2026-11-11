package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.HouseRules.JoinBlock;

import org.junit.Test;

public class HouseRulesTest {
    private static House house(String owner, int members, int items, int chores) {
        return new House("h", owner, members, items, chores, null, null, null, null);
    }

    @Test
    public void joinOnlyFromOwnEmptyOnePersonHouse() {
        assertEquals(JoinBlock.NONE, HouseRules.canJoinFrom(house("carol", 1, 0, 0), "carol"));
        assertEquals(JoinBlock.NOT_OWNER_OF_CURRENT, HouseRules.canJoinFrom(house("alice", 2, 0, 0), "bob"));
        assertEquals(JoinBlock.CURRENT_HOUSE_NOT_EMPTY, HouseRules.canJoinFrom(house("dave", 1, 1, 0), "dave"));
        assertEquals(JoinBlock.CURRENT_HOUSE_NOT_EMPTY, HouseRules.canJoinFrom(house("dave", 1, 0, 2), "dave"));
        assertEquals(JoinBlock.CURRENT_HOUSE_NOT_EMPTY, HouseRules.canJoinFrom(house("alice", 2, 0, 0), "alice"));
    }

    @Test
    public void ownerStaysMembersMayLeave() {
        assertFalse(HouseRules.canLeave(house("alice", 2, 0, 0), "alice"));
        assertTrue(HouseRules.canLeave(house("alice", 2, 0, 0), "bob"));
    }

    @Test
    public void onlyTheOwnerRemovesOthers() {
        House shared = house("alice", 3, 0, 0);

        assertTrue(HouseRules.canRemove(shared, "alice", "bob"));
        assertFalse(HouseRules.canRemove(shared, "alice", "alice"));
        assertFalse(HouseRules.canRemove(shared, "bob", "carol"));
    }

    @Test
    public void roomUntilSixMembers() {
        assertTrue(house("a", 5, 0, 0).hasRoom());
        assertFalse(house("a", 6, 0, 0).hasRoom());
    }

    @Test
    public void regionMustFitTheForecastGrid() {
        assertTrue(HouseRules.isValidRegion("서울특별시 중구", 60, 127));
        assertTrue(HouseRules.isValidRegion("끝", 149, 253));
        assertFalse(HouseRules.isValidRegion("  ", 60, 127));
        assertFalse(HouseRules.isValidRegion("서울", 0, 127));
        assertFalse(HouseRules.isValidRegion("서울", 60, 254));
        assertFalse(HouseRules.isValidRegion("가나다라마바사아자차카타파하가나다라마바사아자차카타파하가나다", 60, 127));
    }
}
