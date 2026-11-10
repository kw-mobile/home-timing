package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.DENIED;
import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static com.kwmobile.hometiming.rules.FirestoreEmulator.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.rules.FirestoreEmulator.Response;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The committed bootstrap rules deny every app request. These tests also prove the harness tells an
 * allowed request (admin, status 200) from a rules denial (status 403), so later rule tests can rely
 * on it. Replace them when docs/DATA_MODEL.md rules land.
 */
public class BootstrapRulesTest {
    private static FirestoreEmulator firestore;

    @BeforeClass
    public static void connect() {
        firestore = FirestoreEmulator.connect();
    }

    @Before
    public void clearData() {
        firestore.clear();
        firestore.seed("users/alice", Fields.of()
                .str("currentHouseId", "house-a")
                .str("displayName", "앨리스")
                .str("notifyTime", "09:00")
                .num("advanceDays", 3));
    }

    @Test
    public void adminSeesSeededDocument() {
        Response response = firestore.get(Auth.admin(), "users/alice");

        assertEquals(response.toString(), OK, response.status());
        assertTrue(response.body(), response.body().contains("currentHouseId"));
    }

    @Test
    public void signedOutReadIsDenied() {
        assertStatus(DENIED, firestore.get(Auth.signedOut(), "users/alice"));
    }

    @Test
    public void userCannotReadOwnDocumentYet() {
        assertStatus(DENIED, firestore.get(Auth.user("alice"), "users/alice"));
    }

    @Test
    public void userCannotWriteOrDeleteOwnDocumentYet() {
        assertStatus(DENIED, firestore.set(Auth.user("alice"), "users/alice",
                Fields.of().str("displayName", "변경")));
        assertStatus(DENIED, firestore.delete(Auth.user("alice"), "users/alice"));
    }

    @Test
    public void adminCommitAppliesTransformsAtomically() {
        Response commit = firestore.commit(Auth.admin(),
                Write.create("houses/house-b", Fields.of().str("ownerId", "bob").num("memberCount", 0))
                        .increment("memberCount", 1)
                        .serverTime("createdAt"),
                Write.create("houses/house-b/members/bob", Fields.of().str("displayName", "밥")));

        assertStatus(OK, commit);
        Response house = firestore.get(Auth.admin(), "houses/house-b");
        assertStatus(OK, house);
        assertTrue(house.body(), house.body().matches("(?s).*\"memberCount\"\\s*:\\s*\\{\\s*\"integerValue\"\\s*:\\s*\"1\".*"));
        assertTrue(house.body(), house.body().contains("createdAt"));
    }

    @Test
    public void deniedCommitWritesNothing() {
        Response commit = firestore.commit(Auth.user("bob"),
                Write.create("users/bob", Fields.of().str("currentHouseId", "house-b")),
                Write.create("houses/house-b", Fields.of().str("ownerId", "bob")).increment("memberCount", 1),
                Write.create("houses/house-b/members/bob", Fields.of().str("displayName", "밥")));

        assertStatus(DENIED, commit);
        assertStatus(NOT_FOUND, firestore.get(Auth.admin(), "users/bob"));
        assertStatus(NOT_FOUND, firestore.get(Auth.admin(), "houses/house-b"));
    }

    private static void assertStatus(int expected, Response response) {
        assertEquals(response.toString(), expected, response.status());
    }
}
