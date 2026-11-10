package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.rules.FirestoreEmulator.Response;

import org.junit.Test;

/** Checks the harness itself: admin seeding and commits work, and a denied commit writes nothing. */
public class HarnessTest extends RulesTestBase {
    @Test
    public void adminSeedIsReadable() {
        seedUser("alice", "house-a");

        Response response = firestore.get(Auth.admin(), "users/alice");

        assertAllowed(response);
        assertTrue(response.body(), response.body().contains("currentHouseId"));
    }

    @Test
    public void adminCommitAppliesTransforms() {
        assertAllowed(firestore.commit(Auth.admin(),
                Write.create("houses/house-b", Fields.of().str("ownerId", "bob").num("memberCount", 0))
                        .increment("memberCount", 1)
                        .serverTime("createdAt")));

        Response house = firestore.get(Auth.admin(), "houses/house-b");
        assertAllowed(house);
        assertTrue(house.body(), house.body().matches(
                "(?s).*\"memberCount\"\\s*:\\s*\\{\\s*\"integerValue\"\\s*:\\s*\"1\".*"));
    }

    @Test
    public void deniedCommitWritesNothing() {
        assertDenied(firestore.commit(Auth.signedOut(), firstSignIn("bob", "house-b", "밥")));

        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "users/bob").status());
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), "houses/house-b").status());
    }
}
