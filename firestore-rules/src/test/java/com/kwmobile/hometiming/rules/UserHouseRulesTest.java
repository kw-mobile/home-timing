package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** users, houses and members (docs/DATA_MODEL.md, 최초 로그인·소속 복구). */
public class UserHouseRulesTest extends RulesTestBase {
    private static final Auth ALICE = Auth.user("alice");
    private static final Auth BOB = Auth.user("bob");

    // ---- First sign-in ----

    @Test
    public void firstSignInCreatesUserHouseAndMember() {
        assertAllowed(firestore.commit(ALICE, firstSignIn("alice", "house-a", "앨리스")));

        assertAllowed(firestore.get(ALICE, "users/alice"));
        assertAllowed(firestore.get(ALICE, "houses/house-a"));
        assertAllowed(firestore.get(ALICE, "houses/house-a/members/alice"));
    }

    @Test
    public void firstSignInNeedsAllThreeDocuments() {
        Write[] writes = firstSignIn("alice", "house-a", "앨리스");

        assertDenied(firestore.commit(ALICE, writes[0], writes[1]));
        assertDenied(firestore.commit(ALICE, writes[1], writes[2]));
        assertDenied(firestore.commit(ALICE, writes[0], writes[2]));
    }

    @Test
    public void cannotFoundHouseForSomeoneElse() {
        assertDenied(firestore.commit(ALICE, firstSignIn("bob", "house-b", "밥")));
        assertDenied(firestore.commit(ALICE,
                firstSignIn("alice", "house-a", "앨리스")[0],
                newHouse("house-a", "bob"),
                newMember("house-a", "alice", "앨리스")));
    }

    @Test
    public void newHouseMustBeEmptyAndOnePerson() {
        Write user = firstSignIn("alice", "house-a", "앨리스")[0];
        Write member = newMember("house-a", "alice", "앨리스");

        assertDenied(firestore.commit(ALICE, user, member, Write.set("houses/house-a", Fields.of()
                .str("ownerId", "alice").num("memberCount", 2).num("itemCount", 0).num("choreCount", 0))
                .serverTime("createdAt").serverTime("updatedAt")));
        assertDenied(firestore.commit(ALICE, user, member, Write.set("houses/house-a", Fields.of()
                .str("ownerId", "alice").num("memberCount", 1).num("itemCount", 5).num("choreCount", 0))
                .serverTime("createdAt").serverTime("updatedAt")));
    }

    @Test
    public void userDocumentFieldsAreChecked() {
        Write house = newHouse("house-a", "alice");
        Write member = newMember("house-a", "alice", "가".repeat(21));
        Write longName = Write.set("users/alice", Fields.of()
                .str("currentHouseId", "house-a").str("displayName", "가".repeat(21))
                .str("notifyTime", "09:00").num("advanceDays", 3))
                .serverTime("createdAt").serverTime("updatedAt");
        assertDenied(firestore.commit(ALICE, longName, house, member));

        Write extraField = Write.set("users/alice", Fields.of()
                .str("currentHouseId", "house-a").str("displayName", "앨리스")
                .str("notifyTime", "09:00").num("advanceDays", 3).bool("admin", true))
                .serverTime("createdAt").serverTime("updatedAt");
        assertDenied(firestore.commit(ALICE, extraField, house, newMember("house-a", "alice", "앨리스")));

        Write clientTime = Write.set("users/alice", Fields.of()
                .str("currentHouseId", "house-a").str("displayName", "앨리스")
                .str("notifyTime", "09:00").num("advanceDays", 3)
                .time("createdAt", SEEDED_AT).time("updatedAt", SEEDED_AT));
        assertDenied(firestore.commit(ALICE, clientTime, house, newMember("house-a", "alice", "앨리스")));
    }

    @Test
    public void twentyCharacterKoreanNameIsAccepted() {
        assertAllowed(firestore.commit(ALICE, firstSignIn("alice", "house-a", "가".repeat(20))));
    }

    @Test
    public void memberNameMustMatchUserName() {
        assertDenied(firestore.commit(ALICE,
                firstSignIn("alice", "house-a", "앨리스")[0],
                newHouse("house-a", "alice"),
                newMember("house-a", "alice", "다른이름")));
    }

    // ---- Reading ----

    @Test
    public void onlyOwnUserDocumentIsReadable() {
        seedHouse("house-a", 0, "alice", "bob");

        assertAllowed(firestore.get(ALICE, "users/alice"));
        assertDenied(firestore.get(ALICE, "users/bob"));
        assertDenied(firestore.get(Auth.signedOut(), "users/alice"));
    }

    @Test
    public void onlyMembersReadHouseAndMembers() {
        seedHouse("house-a", 0, "alice", "bob");
        seedHouse("house-c", 0, "carol");

        assertAllowed(firestore.get(BOB, "houses/house-a"));
        assertAllowed(firestore.get(BOB, "houses/house-a/members/alice"));
        assertDenied(firestore.get(Auth.user("carol"), "houses/house-a"));
        assertDenied(firestore.get(Auth.user("carol"), "houses/house-a/members/alice"));
        assertDenied(firestore.get(Auth.signedOut(), "houses/house-a"));
    }

    @Test
    public void removedUserCanSeeTheirMemberDocumentIsGone() {
        seedHouse("house-a", 0, "alice");
        seedUser("bob", "house-a"); // bob was removed: no member document

        assertEquals(NOT_FOUND, firestore.get(BOB, "houses/house-a/members/bob").status());
        assertDenied(firestore.get(BOB, "houses/house-a"));
    }

    // ---- Updating ----

    @Test
    public void notificationSettingsCanChange() {
        seedHouse("house-a", 0, "alice");

        assertAllowed(firestore.commit(ALICE, Write.merge("users/alice",
                Fields.of().str("notifyTime", "07:30").num("advanceDays", 0)).serverTime("updatedAt")));
        assertDenied(firestore.commit(ALICE, Write.merge("users/alice",
                Fields.of().str("notifyTime", "25:00")).serverTime("updatedAt")));
        assertDenied(firestore.commit(ALICE, Write.merge("users/alice",
                Fields.of().str("displayName", "새이름")).serverTime("updatedAt")));
    }

    @Test
    public void removedUserRecoversIntoNewHouse() {
        seedHouse("house-a", 0, "alice");
        seedUser("bob", "house-a");

        assertAllowed(firestore.commit(BOB,
                newHouse("house-b", "bob"),
                newMember("house-b", "bob", nameOf("bob")),
                Write.merge("users/bob", Fields.of().str("currentHouseId", "house-b")).serverTime("updatedAt")));
    }

    @Test
    public void cannotPointAtAHouseWithoutMembership() {
        seedHouse("house-a", 0, "alice");
        seedHouse("house-b", 0, "bob");

        assertDenied(firestore.commit(BOB, Write.merge("users/bob",
                Fields.of().str("currentHouseId", "house-a")).serverTime("updatedAt")));
    }

    @Test
    public void cannotJoinExistingHouseYet() {
        seedHouse("house-a", 0, "alice");
        seedHouse("house-b", 0, "bob");

        assertDenied(firestore.commit(BOB,
                newMember("house-a", "bob", nameOf("bob")),
                Write.merge("users/bob", Fields.of().str("currentHouseId", "house-a")).serverTime("updatedAt")));
    }

    @Test
    public void houseOwnerAndCountsCannotBeRewritten() {
        seedHouse("house-a", 0, "alice", "bob");

        assertDenied(firestore.commit(BOB, Write.merge("houses/house-a", Fields.of().str("ownerId", "bob"))));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/house-a", Fields.of().num("memberCount", 6))));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/house-a", Fields.of()).increment("itemCount", 2)));
        assertDenied(firestore.commit(ALICE, Write.merge("houses/house-a", Fields.of()).increment("itemCount", -1)));
    }

    @Test
    public void usersAndHousesCannotBeDeletedWhileInUse() {
        seedHouse("house-a", 0, "alice");

        assertDenied(firestore.delete(ALICE, "users/alice"));
        assertDenied(firestore.delete(ALICE, "houses/house-a"));
        assertDenied(firestore.delete(ALICE, "houses/house-a/members/alice"));
    }

    @Test
    public void choresStayClosedAndBareInvitesAreDenied() {
        seedHouse("house-a", 0, "alice");

        assertDenied(firestore.get(ALICE, "houses/house-a/chores/c1"));
        assertDenied(firestore.set(ALICE, "invites/123456", Fields.of().str("houseId", "house-a")));
    }
}
