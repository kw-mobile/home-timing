package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/** Account deletion (docs/DATA_MODEL.md 정리 정책, #40). Auth deletion itself is the app's last step. */
public class AccountDeletionRulesTest extends RulesTestBase {
    private static final Auth ALICE = Auth.user("alice"); // owner of shared house-a
    private static final Auth BOB = Auth.user("bob");     // member of house-a
    private static final Auth CAROL = Auth.user("carol"); // alone in empty house-c
    private static final Auth DAVE = Auth.user("dave");   // alone in house-d with an item

    @Before
    public void seedHouses() {
        seedHouse("house-a", 0, "alice", "bob");
        seedHouse("house-c", 0, "carol");
        seedHouse("house-d", 1, "dave");
        seedItem("house-d", "filter", "dave", today(), 30, "occ-1", 1);
    }

    private static Write[] dissolveAndDelete(String uid, String houseId) {
        return new Write[] {
            Write.delete("houses/" + houseId + "/members/" + uid),
            Write.delete("houses/" + houseId),
            Write.delete("users/" + uid)
        };
    }

    @Test
    public void memberLeavesAndDeletesTheirUser() {
        assertAllowed(firestore.commit(BOB, removeMember("house-a", "bob")[0], removeMember("house-a", "bob")[1],
                Write.delete("users/bob"), deletionReceipt("bob")));

        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "users/bob").status());
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "houses/house-a/members/bob").status());
    }

    @Test
    public void userCannotBeDeletedWhileStillAMember() {
        assertDenied(firestore.commit(BOB, Write.delete("users/bob")));
        assertDenied(firestore.delete(BOB, "users/bob"));
    }

    @Test
    public void ownerAloneDissolvesTheEmptyHouseWithTheUser() {
        assertAllowed(firestore.commit(CAROL, deletionReceipt("carol"), closeHouse("house-c")));
        assertAllowed(firestore.commit(CAROL, dissolveAndDelete("carol", "house-c")));

        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "houses/house-c").status());
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "users/carol").status());
    }

    @Test
    public void houseWithItemsCannotBeDissolved() {
        assertDenied(firestore.commit(DAVE, dissolveAndDelete("dave", "house-d")));
    }

    @Test
    public void ownerAloneAfterDeletingItemsCanDissolve() {
        assertAllowed(firestore.commit(DAVE, deletionReceipt("dave"), closeHouse("house-d")));
        assertAllowed(firestore.commit(DAVE, startDeleting(itemPath("house-d", "filter"), "dave")));
        assertAllowed(firestore.commit(DAVE,
                Write.delete(itemPath("house-d", "filter")),
                Write.merge("houses/house-d", Fields.of().str("itemMutationId", "filter")).increment("itemCount", -1)));

        assertAllowed(firestore.commit(DAVE, dissolveAndDelete("dave", "house-d")));
    }

    @Test
    public void houseWithChoresCannotBeDissolved() {
        assertAllowed(firestore.commit(Auth.admin(), Write.merge("houses/house-c", Fields.of().num("choreCount", 1))));

        assertDenied(firestore.commit(CAROL, dissolveAndDelete("carol", "house-c")));
    }

    @Test
    public void ownerOfASharedHouseCannotDissolveIt() {
        assertDenied(firestore.commit(ALICE, dissolveAndDelete("alice", "house-a")));
        // Nor leave it as a member would.
        assertDenied(firestore.commit(ALICE, removeMember("house-a", "alice")[0],
                removeMember("house-a", "alice")[1], Write.delete("users/alice")));
    }

    @Test
    public void removedUserDeletesOnlyTheirUserDocument() {
        assertAllowed(firestore.commit(ALICE, removeMember("house-a", "bob")));

        assertAllowed(firestore.commit(BOB, deletionReceipt("bob"), Write.delete("users/bob")));
    }

    @Test
    public void nobodyDeletesSomeoneElsesUser() {
        assertAllowed(firestore.commit(ALICE, removeMember("house-a", "bob")));

        assertDenied(firestore.commit(ALICE, Write.delete("users/bob")));
    }

    @Test
    public void dissolvingKeepsTheUserMovingElsewhereRule() {
        // Without deleting the user, the house can be dissolved only if the user points elsewhere.
        assertDenied(firestore.commit(CAROL,
                Write.delete("houses/house-c/members/carol"),
                Write.delete("houses/house-c")));
    }
}
