package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

/** Account/house lifecycle flows, including competing devices and malicious direct writes. */
public class AccountLifecycleRulesTest extends RulesTestBase {
    private static final Auth ALICE = Auth.user("alice");
    private static final Auth BOB = Auth.user("bob");
    private static final String ITEM = "houses/home/items/filter";

    @Before
    public void seedHomes() {
        seedHouse("home", 1, "alice");
        seedHouse("bob-home", 0, "bob");
        seedItem("home", "filter", "alice", today(), 30, "first", 1);
        seedInvite("123456", "home", "alice", Instant.now());
        seedActiveInvite("home", "123456");
    }

    private static Write[] beginDeletion() {
        return new Write[] {deletionReceipt("alice"), closeHouse("home"), Write.delete("invites/123456")};
    }

    @Test
    public void closingHouseRejectsJoinAndNewDataButAllowsResumableCleanup() {
        assertAllowed(firestore.commit(ALICE, beginDeletion()));
        assertDenied(firestore.commit(BOB, join("bob", "home", "123456", "bob-home")));
        assertDenied(firestore.commit(ALICE, createItem("home", "new",
                itemFields("alice", today(), today(), 30, "new-first", 1))));
        assertDenied(firestore.commit(ALICE, Write.merge(ITEM, Fields.of()
                .str("name", "unexpected edit").str("updatedBy", "alice")).serverTime("updatedAt")));
        assertAllowed(firestore.commit(ALICE, startDeleting(ITEM, "alice")));
        // A retry may read the frozen data, but cannot reopen the house or clear the receipt.
        assertAllowed(firestore.get(ALICE, ITEM));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/home", Fields.of().bool("closing", false))));
        assertDenied(firestore.commit(ALICE, Write.delete("accountDeletions/alice")));
        assertAllowed(firestore.commit(ALICE, Write.delete(ITEM), Write.merge("houses/home",
                Fields.of().str("itemMutationId", "filter")).increment("itemCount", -1)));
        assertAllowed(firestore.commit(ALICE, Write.delete("houses/home/members/alice"),
                Write.delete("houses/home"), Write.delete("users/alice")));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), ITEM).status());
        // Another device with the same still-valid Auth identity cannot recreate data.
        assertDenied(firestore.commit(Auth.user("alice"), firstSignIn("alice", "resurrected", nameOf("alice"))));
        assertAllowed(firestore.get(ALICE, "accountDeletions/alice"));
    }

    @Test
    public void joinWinningTheRaceRejectsDeletionBeforeAnyDataIsRemoved() {
        assertAllowed(firestore.commit(BOB, join("bob", "home", "123456", "bob-home")));
        assertDenied(firestore.commit(ALICE, beginDeletion()));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "accountDeletions/alice").status());
        assertAllowed(firestore.get(BOB, ITEM));
    }

    @Test
    public void movingToANewHouseMustRemoveOldMembershipAndStaleAuthCannotStartDeletion() {
        assertDenied(firestore.commit(ALICE, newHouse("bypass", "alice"),
                newMember("bypass", "alice", nameOf("alice")),
                Write.merge("users/alice", Fields.of().str("currentHouseId", "bypass")).serverTime("updatedAt")));
        Auth stale = Auth.userWithAuthTime("alice", Instant.now().minusSeconds(600).getEpochSecond());
        assertDenied(firestore.commit(stale, beginDeletion()));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "accountDeletions/alice").status());
        assertAllowed(firestore.get(ALICE, ITEM));
    }

    private static Write[] complete(String before, String after, int revision, int count) {
        return new Write[] {
            Write.merge(ITEM, Fields.of().str("occurrenceId", after).num("revision", revision)
                    .num("recordCount", count).str("recordMutationId", before).str("updatedBy", "alice"))
                    .serverTime("updatedAt"),
            Write.create(ITEM + "/history/" + before, Fields.of().str("replacedDate", today())
                    .str("previousDueDate", daysFromToday(30)).num("cycleDaysUsed", 30)
                    .str("completedBy", "alice").str("completedByName", nameOf("alice")))
                    .serverTime("createdAt")
        };
    }

    @Test
    public void completionRejectsUsedOrUnsafeNextIdsAndThenAcceptsAFreshOccurrence() {
        assertAllowed(firestore.commit(ALICE, complete("first", "second", 2, 1)));
        assertDenied(firestore.commit(ALICE, complete("second", "first", 3, 2)));
        assertDenied(firestore.commit(ALICE, complete("second", "bad/id", 3, 2)));
        assertAllowed(firestore.commit(ALICE, complete("second", "third", 3, 2)));
    }
}
