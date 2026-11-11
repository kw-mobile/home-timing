package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;

/** invites, join, leave, remove and region (docs/DATA_MODEL.md 가족 공유, #36). */
public class FamilyRulesTest extends RulesTestBase {
    private static final Auth ALICE = Auth.user("alice"); // owner of house-a
    private static final Auth BOB = Auth.user("bob");     // member of house-a
    private static final Auth CAROL = Auth.user("carol"); // alone in empty house-c, joins
    private static final Auth DAVE = Auth.user("dave");   // alone in house-d with an item

    private static final Instant LONG_AGO = Instant.now().minus(Duration.ofHours(25));

    @Before
    public void seedHouses() {
        seedHouse("house-a", 0, "alice", "bob");
        seedHouse("house-c", 0, "carol");
        seedHouse("house-d", 1, "dave");
    }

    private static void aliceInvites(String code) {
        assertAllowed(firestore.commit(ALICE, issueInvite("house-a", "alice", code, null)));
    }

    // ---- Issuing ----

    @Test
    public void ownerIssuesInviteAnyoneSignedInCanLookItUp() {
        aliceInvites("123456");

        assertAllowed(firestore.get(CAROL, "invites/123456"));
        assertDenied(firestore.get(Auth.signedOut(), "invites/123456"));
    }

    @Test
    public void invitesCannotBeListed() {
        aliceInvites("123456");

        assertDenied(firestore.get(CAROL, "invites"));
    }

    @Test
    public void onlyTheOwnerIssues() {
        assertDenied(firestore.commit(BOB, issueInvite("house-a", "bob", "123456", null)));
        assertDenied(firestore.commit(CAROL, issueInvite("house-a", "carol", "123456", null)));
    }

    @Test
    public void inviteAndActiveCodeGoTogether() {
        Write[] writes = issueInvite("house-a", "alice", "123456", null);

        assertDenied(firestore.commit(ALICE, writes[0]));
        assertDenied(firestore.commit(ALICE, writes[1]));
    }

    @Test
    public void inviteShapeIsChecked() {
        Write house = Write.merge("houses/house-a", Fields.of().str("activeInviteCode", "123456"));

        assertDenied(firestore.commit(ALICE, house, Write.set("invites/123456",
                Fields.of().str("houseId", "house-a").str("createdBy", "alice").time("createdAt", Instant.now()))));
        assertDenied(firestore.commit(ALICE, house, Write.set("invites/123456",
                Fields.of().str("houseId", "house-a").str("createdBy", "alice").time("expiresAt", Instant.now()))
                .serverTime("createdAt")));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/house-a", Fields.of().str("activeInviteCode", "12345")),
                Write.set("invites/12345", Fields.of().str("houseId", "house-a").str("createdBy", "alice"))
                        .serverTime("createdAt")));
    }

    @Test
    public void liveCodeOfAnotherHouseCannotBeTaken() {
        aliceInvites("123456");

        assertDenied(firestore.commit(DAVE, issueInvite("house-d", "dave", "123456", null)));
    }

    @Test
    public void expiredCodeCanBeReused() {
        seedInvite("222222", "house-old", "someone", LONG_AGO);

        assertAllowed(firestore.commit(ALICE, issueInvite("house-a", "alice", "222222", null)));
    }

    @Test
    public void ownerStopsInviting() {
        aliceInvites("123456");

        assertAllowed(firestore.commit(ALICE,
                Write.merge("houses/house-a", Fields.of()).remove("activeInviteCode"),
                Write.delete("invites/123456")));
        assertDenied(firestore.commit(CAROL, join("carol", "house-a", "123456", "house-c")));
    }

    // ---- Joining ----

    @Test
    public void joinWithLiveInviteMovesIntoTheHouse() {
        aliceInvites("123456");

        assertAllowed(firestore.commit(CAROL, join("carol", "house-a", "123456", "house-c")));
        assertAllowed(firestore.get(CAROL, "houses/house-a"));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "houses/house-c").status());
    }

    @Test
    public void oneCodeAdmitsSeveralPeople() {
        seedHouse("house-e", 0, "erin");
        aliceInvites("123456");

        assertAllowed(firestore.commit(CAROL, join("carol", "house-a", "123456", "house-c")));
        assertAllowed(firestore.commit(Auth.user("erin"), join("erin", "house-a", "123456", "house-e")));
    }

    @Test
    public void nonMemberCannotReadTheHouseBeforeJoining() {
        aliceInvites("123456");

        assertDenied(firestore.get(CAROL, "houses/house-a"));
    }

    @Test
    public void joinMustDeleteTheOldEmptyHouse() {
        aliceInvites("123456");
        Write[] writes = join("carol", "house-a", "123456", "house-c");

        assertDenied(firestore.commit(CAROL, writes[0], writes[1], writes[2]));
        assertDenied(firestore.commit(CAROL, writes[0], writes[1], writes[2], writes[3]));
    }

    @Test
    public void cannotJoinFromAHouseWithItems() {
        aliceInvites("123456");

        assertDenied(firestore.commit(DAVE, join("dave", "house-a", "123456", "house-d")));
    }

    @Test
    public void memberOfASharedHouseMustLeaveFirst() {
        seedHouse("house-x", 0, "xavier");
        seedActiveInvite("house-x", "654321");
        seedInvite("654321", "house-x", "xavier", Instant.now());

        // bob is not the owner of house-a, so house-a cannot be dissolved by him.
        assertDenied(firestore.commit(BOB, join("bob", "house-x", "654321", "house-a")));
    }

    @Test
    public void expiredInviteIsRejected() {
        seedInvite("123456", "house-a", "alice", LONG_AGO);
        seedActiveInvite("house-a", "123456");

        assertDenied(firestore.commit(CAROL, join("carol", "house-a", "123456", "house-c")));
    }

    @Test
    public void reissuedCodeIsNoLongerValid() {
        aliceInvites("111111");
        assertAllowed(firestore.commit(ALICE, issueInvite("house-a", "alice", "333333", "111111")));

        assertDenied(firestore.commit(CAROL, join("carol", "house-a", "111111", "house-c")));
        assertAllowed(firestore.commit(CAROL, join("carol", "house-a", "333333", "house-c")));
    }

    @Test
    public void inviteForAnotherHouseIsRejected() {
        seedInvite("444444", "house-d", "dave", Instant.now());
        seedActiveInvite("house-a", "444444");

        assertDenied(firestore.commit(CAROL, join("carol", "house-a", "444444", "house-c")));
    }

    @Test
    public void fullHouseRejectsTheSeventhMember() {
        seedHouse("house-f", 0, "f1", "f2", "f3", "f4", "f5", "f6");
        seedActiveInvite("house-f", "555555");
        seedInvite("555555", "house-f", "f1", Instant.now());

        assertDenied(firestore.commit(CAROL, join("carol", "house-f", "555555", "house-c")));
    }

    @Test
    public void joinMustRaiseTheCountByOne() {
        aliceInvites("123456");
        Write[] writes = join("carol", "house-a", "123456", "house-c");

        assertDenied(firestore.commit(CAROL, writes[0], writes[2], writes[3], writes[4]));
    }

    @Test
    public void countCannotRiseWithoutANewMember() {
        assertDenied(firestore.commit(CAROL, Write.merge("houses/house-a", Fields.of()).increment("memberCount", 1)));
        assertDenied(firestore.commit(BOB, Write.merge("houses/house-a", Fields.of()).increment("memberCount", 1)));
    }

    // ---- Leaving and removing ----

    @Test
    public void memberLeavesIntoANewHouse() {
        assertAllowed(firestore.commit(BOB, leave("bob", "house-a", "house-b")));

        assertDenied(firestore.get(BOB, "houses/house-a"));
        assertAllowed(firestore.get(BOB, "houses/house-b"));
    }

    @Test
    public void ownerCannotLeaveWhileOthersRemain() {
        assertDenied(firestore.commit(ALICE, leave("alice", "house-a", "house-new")));
    }

    @Test
    public void ownerRemovesAMemberWhoThenRecovers() {
        assertAllowed(firestore.commit(ALICE, removeMember("house-a", "bob")));

        assertEquals(NOT_FOUND, firestore.get(BOB, "houses/house-a/members/bob").status());
        assertDenied(firestore.get(BOB, "houses/house-a"));
        assertAllowed(firestore.commit(BOB,
                newHouse("house-b", "bob"),
                newMember("house-b", "bob", nameOf("bob")),
                Write.merge("users/bob", Fields.of().str("currentHouseId", "house-b")).serverTime("updatedAt")));
    }

    @Test
    public void onlyTheOwnerRemovesOthers() {
        assertDenied(firestore.commit(BOB, removeMember("house-a", "alice")));
        assertDenied(firestore.commit(ALICE, removeMember("house-a", "alice")));
        assertDenied(firestore.commit(ALICE, Write.delete("houses/house-a/members/bob")));
    }

    @Test
    public void ownersCannotDissolveSomeoneElsesHouse() {
        assertDenied(firestore.commit(ALICE, Write.delete("houses/house-c/members/carol"), Write.delete("houses/house-c")));
    }

    // ---- Region ----

    @Test
    public void onlyTheOwnerSetsTheRegion() {
        Fields seoul = Fields.of().str("regionName", "서울특별시 중구").num("nx", 60).num("ny", 127);

        assertAllowed(firestore.commit(ALICE, Write.merge("houses/house-a", seoul)));
        assertDenied(firestore.commit(BOB, Write.merge("houses/house-a", seoul)));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/house-a",
                Fields.of().str("regionName", "범위 밖").num("nx", 0).num("ny", 127))));
    }
}
