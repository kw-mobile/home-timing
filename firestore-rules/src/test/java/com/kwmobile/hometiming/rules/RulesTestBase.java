package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.DENIED;
import static com.kwmobile.hometiming.rules.FirestoreEmulator.OK;
import static org.junit.Assert.assertEquals;

import com.kwmobile.hometiming.rules.FirestoreEmulator.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Shared fixtures that mirror docs/DATA_MODEL.md and the writes the app makes. Seeds use admin
 * access; every request under test goes through {@link Auth#user(String)} or signed out.
 */
abstract class RulesTestBase {
    static final Instant SEEDED_AT = Instant.parse("2026-11-01T00:00:00Z");

    static FirestoreEmulator firestore;

    @BeforeClass
    public static void connect() {
        firestore = FirestoreEmulator.connect();
    }

    @Before
    public void clearData() {
        firestore.clear();
    }

    /** Today in Asia/Seoul as stored (the rules compare against request time in Seoul). */
    static String today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
    }

    static String daysFromToday(int days) {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(days).toString();
    }

    static String plusDays(String date, int days) {
        return LocalDate.parse(date).plusDays(days).toString();
    }

    // ---- Seeding (admin) ----

    /** A house owned by the first member, with the member and user documents pointing at it. */
    static void seedHouse(String houseId, int itemCount, String ownerId, String... otherMembers) {
        firestore.seed("houses/" + houseId, Fields.of()
                .str("ownerId", ownerId)
                .num("memberCount", 1 + otherMembers.length)
                .num("itemCount", itemCount)
                .num("choreCount", 0)
                .time("createdAt", SEEDED_AT)
                .time("updatedAt", SEEDED_AT));
        seedMember(houseId, ownerId);
        for (String member : otherMembers) {
            seedMember(houseId, member);
        }
    }

    static void seedMember(String houseId, String uid) {
        firestore.seed("houses/" + houseId + "/members/" + uid, Fields.of()
                .str("displayName", nameOf(uid))
                .time("joinedAt", SEEDED_AT));
        seedUser(uid, houseId);
    }

    static void seedUser(String uid, String houseId) {
        firestore.seed("users/" + uid, Fields.of()
                .str("currentHouseId", houseId)
                .str("displayName", nameOf(uid))
                .str("notifyTime", "09:00")
                .num("advanceDays", 3)
                .time("createdAt", SEEDED_AT)
                .time("updatedAt", SEEDED_AT));
    }

    /** An item registered by {@code createdBy}, started {@code startDate}, never completed. */
    static void seedItem(String houseId, String itemId, String createdBy, String startDate, int cycleDays,
                         String occurrenceId, int revision) {
        firestore.seed(itemPath(houseId, itemId), itemFields(createdBy, startDate, startDate, cycleDays,
                occurrenceId, revision).time("createdAt", SEEDED_AT).time("updatedAt", SEEDED_AT));
    }

    static String nameOf(String uid) {
        return "이름-" + uid;
    }

    static String itemPath(String houseId, String itemId) {
        return "houses/" + houseId + "/items/" + itemId;
    }

    static String historyPath(String houseId, String itemId, String occurrenceId) {
        return itemPath(houseId, itemId) + "/history/" + occurrenceId;
    }

    // ---- Writes the app makes ----

    /** AuthRepository first sign-in: user + one-person house + member, in one commit. */
    static Write[] firstSignIn(String uid, String houseId, String name) {
        return new Write[] {
            Write.set("users/" + uid, Fields.of()
                    .str("currentHouseId", houseId)
                    .str("displayName", name)
                    .str("notifyTime", "09:00")
                    .num("advanceDays", 3))
                    .serverTime("createdAt").serverTime("updatedAt"),
            newHouse(houseId, uid),
            newMember(houseId, uid, name)
        };
    }

    static Write newHouse(String houseId, String ownerId) {
        return Write.set("houses/" + houseId, Fields.of()
                .str("ownerId", ownerId)
                .num("memberCount", 1)
                .num("itemCount", 0)
                .num("choreCount", 0))
                .serverTime("createdAt").serverTime("updatedAt");
    }

    static Write newMember(String houseId, String uid, String name) {
        return Write.set("houses/" + houseId + "/members/" + uid, Fields.of().str("displayName", name))
                .serverTime("joinedAt");
    }

    /** Required item fields except createdAt/updatedAt. */
    static Fields itemFields(String uid, String startDate, String lastReplacedDate, int cycleDays,
                             String occurrenceId, int revision) {
        return Fields.of()
                .str("name", "샤워기 필터")
                .str("category", "ETC")
                .str("startDate", startDate)
                .num("cycleDays", cycleDays)
                .str("lastReplacedDate", lastReplacedDate)
                .str("nextDueDate", plusDays(lastReplacedDate, cycleDays))
                .str("occurrenceId", occurrenceId)
                .num("revision", revision)
                .str("createdBy", uid)
                .str("updatedBy", uid);
    }

    /** Item registration: the item plus the house itemCount increment. */
    static Write[] createItem(String houseId, String itemId, Fields fields) {
        return new Write[] {
            Write.create(itemPath(houseId, itemId), fields).serverTime("createdAt").serverTime("updatedAt"),
            Write.merge("houses/" + houseId, Fields.of()).increment("itemCount", 1)
        };
    }

    static void assertAllowed(Response response) {
        assertEquals(response.toString(), OK, response.status());
    }

    static void assertDenied(Response response) {
        assertEquals(response.toString(), DENIED, response.status());
    }
}
