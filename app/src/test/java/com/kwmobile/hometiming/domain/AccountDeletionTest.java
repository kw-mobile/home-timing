package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.domain.AccountDeletion.Step;

import org.junit.Test;

public class AccountDeletionTest {
    private static House house(String owner, int members, int items) {
        return new House("h", owner, members, items, 0, null, null, null, null);
    }

    @Test
    public void nothingStoredLeavesOnlyAuth() {
        assertEquals(Step.DELETE_AUTH_ONLY, AccountDeletion.plan(false, null, false, "u"));
    }

    @Test
    public void removedUserDeletesOnlyTheirUserDocument() {
        assertEquals(Step.DELETE_USER_ONLY, AccountDeletion.plan(true, house("alice", 2, 0), false, "bob"));
        assertEquals(Step.DELETE_USER_ONLY, AccountDeletion.plan(true, null, false, "bob"));
        assertEquals(Step.DELETE_USER_ONLY, AccountDeletion.plan(true, null, true, "bob"));
    }

    @Test
    public void memberLeavesTheSharedHouse() {
        assertEquals(Step.LEAVE_SHARED_HOUSE, AccountDeletion.plan(true, house("alice", 3, 5), true, "bob"));
    }

    @Test
    public void ownerAloneDissolvesEvenWithItems() {
        // Items are deleted first, then the empty house.
        assertEquals(Step.DISSOLVE_OWN_HOUSE, AccountDeletion.plan(true, house("carol", 1, 4), true, "carol"));
    }

    @Test
    public void ownerWithMembersIsBlocked() {
        assertEquals(Step.BLOCKED_OWNER_WITH_MEMBERS, AccountDeletion.plan(true, house("alice", 2, 0), true, "alice"));
    }
}
