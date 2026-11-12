package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.kwmobile.hometiming.data.firestore.HouseholdSetup.Action;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class HouseholdSetupTest {
    private static final Object NOW = new Object();

    @Test
    public void firstSignInCreatesHouse() {
        assertEquals(Action.CREATE_FIRST_HOUSE, HouseholdSetup.decide(false, null, false));
    }

    @Test
    public void memberOfCurrentHouseIsReady() {
        assertEquals(Action.READY, HouseholdSetup.decide(true, "house-a", true));
    }

    @Test
    public void removedMemberRecoversToNewHouse() {
        assertEquals(Action.RECOVER_HOUSE, HouseholdSetup.decide(true, "house-a", false));
        assertEquals(Action.RECOVER_HOUSE, HouseholdSetup.decide(true, null, false));
        assertEquals(Action.RECOVER_HOUSE, HouseholdSetup.decide(true, "", true));
    }

    @Test
    public void newUserMatchesDataModel() {
        Map<String, Object> user = HouseholdSetup.newUser("house-a", "민지", NOW);

        assertEquals(Set.of("currentHouseId", "displayName", "notifyTime", "advanceDays", "createdAt", "updatedAt"),
                user.keySet());
        assertEquals("house-a", user.get("currentHouseId"));
        assertEquals("민지", user.get("displayName"));
        assertEquals("09:00", user.get("notifyTime"));
        assertEquals(3, user.get("advanceDays"));
        assertSame(NOW, user.get("createdAt"));
        assertSame(NOW, user.get("updatedAt"));
    }

    @Test
    public void newHouseIsOnePersonAndEmpty() {
        Map<String, Object> house = HouseholdSetup.newHouse("uid-1", NOW);

        assertEquals(Set.of("ownerId", "memberCount", "itemCount", "choreCount", "integrityVersion", "closing", "createdAt", "updatedAt"),
                house.keySet());
        assertEquals("uid-1", house.get("ownerId"));
        assertEquals(1, house.get("memberCount"));
        assertEquals(0, house.get("itemCount"));
        assertEquals(0, house.get("choreCount"));
    }

    @Test
    public void memberAndMoveCarryOnlyTheirFields() {
        assertEquals(Set.of("displayName", "joinedAt"), HouseholdSetup.newMember("민지", NOW).keySet());
        // Recovery keeps the user's notification settings and creation time.
        assertEquals(Set.of("currentHouseId", "updatedAt"), HouseholdSetup.moveUser("house-b", NOW).keySet());
    }

    @Test
    public void fallbackNamePrefersProfileThenEmailLocalPart() {
        assertEquals("민지", HouseholdSetup.fallbackDisplayName(" 민지 ", "minji@kw.ac.kr"));
        assertEquals("minji", HouseholdSetup.fallbackDisplayName(null, "minji@kw.ac.kr"));
        assertEquals("minji", HouseholdSetup.fallbackDisplayName("   ", "minji@kw.ac.kr"));
        assertEquals("abcdefghijklmnopqrst",
                HouseholdSetup.fallbackDisplayName("가".repeat(21), "abcdefghijklmnopqrstuvwxyz@kw.ac.kr"));
        assertEquals(HouseholdSetup.UNNAMED, HouseholdSetup.fallbackDisplayName(null, null));
    }
}
