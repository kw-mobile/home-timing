package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.House;
import com.kwmobile.hometiming.domain.Member;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.Test;

public class HouseDocumentsTest {
    private static final Object NOW = new Object();

    @Test
    public void readsHouseWithOptionalRegionAndInvite() {
        Map<String, Object> stored = new HashMap<>(HouseholdSetup.newHouse("alice", NOW));
        stored.put("memberCount", 2L);
        stored.put("itemCount", 0L);
        stored.put("choreCount", 0L);

        House plain = HouseDocuments.toHouse("house-a", stored);
        assertEquals("alice", plain.ownerId);
        assertEquals(2, plain.memberCount);
        assertFalse(plain.hasRegion());
        assertNull(plain.activeInviteCode);

        stored.putAll(HouseDocuments.regionFields(" 서울특별시 중구 ", 60, 127));
        stored.put("nx", 60L);
        stored.put("ny", 127L);
        stored.put("activeInviteCode", "123456");
        House full = HouseDocuments.toHouse("house-a", stored);
        assertTrue(full.hasRegion());
        assertEquals("서울특별시 중구", full.regionName);
        assertEquals(Integer.valueOf(127), full.ny);
        assertEquals("123456", full.activeInviteCode);
    }

    @Test
    public void malformedHouseIsNull() {
        Map<String, Object> stored = new HashMap<>(HouseholdSetup.newHouse("alice", NOW));
        stored.remove("memberCount");

        assertNull(HouseDocuments.toHouse("house-a", stored));
    }

    @Test
    public void memberKnowsTheOwner() {
        Map<String, Object> stored = HouseholdSetup.newMember("앨리스", NOW);

        Member owner = HouseDocuments.toMember("alice", stored, "alice");
        Member other = HouseDocuments.toMember("bob", stored, "alice");
        assertTrue(owner.owner);
        assertFalse(other.owner);
    }

    @Test
    public void inviteAndJoinFieldsMatchTheRules() {
        Map<String, Object> invite = HouseDocuments.inviteFields("house-a", "alice", NOW);
        assertEquals(new HashSet<>(Arrays.asList("houseId", "createdBy", "createdAt")), invite.keySet());
        assertSame(NOW, invite.get("createdAt"));

        Map<String, Object> member = HouseDocuments.joinedMemberFields("캐롤", "123456", NOW);
        assertEquals(new HashSet<>(Arrays.asList("displayName", "joinedAt", "inviteCode")), member.keySet());
        assertEquals("123456", member.get("inviteCode"));
    }

    @Test
    public void regionFieldsAreTheThreeRegionKeys() {
        assertEquals(new HashSet<>(Arrays.asList("regionName", "nx", "ny")),
                HouseDocuments.regionFields("서울", 60, 127).keySet());
    }
}
