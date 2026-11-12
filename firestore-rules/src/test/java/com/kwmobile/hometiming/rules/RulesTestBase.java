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
                .num("choreCount", 0).num("integrityVersion", 2)
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
                .num("choreCount", 0).num("integrityVersion", 2))
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
                .num("revision", revision).num("recordCount", 0).bool("deleting", false)
                .str("createdBy", uid)
                .str("updatedBy", uid);
    }

    /** Item registration: the item plus the house itemCount increment. */
    static Write[] createItem(String houseId, String itemId, Fields fields) {
        return new Write[] {
            Write.create(itemPath(houseId, itemId), fields).serverTime("createdAt").serverTime("updatedAt"),
            Write.merge("houses/" + houseId, Fields.of().str("itemMutationId", itemId)).increment("itemCount", 1)
        };
    }

    // ---- Family sharing (docs/DATA_MODEL.md 가족 공유) ----

    /** An invite created at {@code createdAt}; it is live for 24 hours from then. */
    static void seedInvite(String code, String houseId, String createdBy, Instant createdAt) {
        firestore.seed("invites/" + code, Fields.of()
                .str("houseId", houseId)
                .str("createdBy", createdBy)
                .time("createdAt", createdAt));
    }

    /** Marks {@code code} as the house's active invite (admin). */
    static void seedActiveInvite(String houseId, String code) {
        assertAllowed(firestore.commit(Auth.admin(),
                Write.merge("houses/" + houseId, Fields.of().str("activeInviteCode", code))));
    }

    /** Issue or reissue: the invite plus the house's active code; deletes {@code previousCode} if given. */
    static Write[] issueInvite(String houseId, String ownerId, String code, String previousCode) {
        Write invite = Write.set("invites/" + code, Fields.of().str("houseId", houseId).str("createdBy", ownerId))
                .serverTime("createdAt");
        Write house = Write.merge("houses/" + houseId, Fields.of().str("activeInviteCode", code));
        return previousCode == null
                ? new Write[] {invite, house}
                : new Write[] {invite, house, Write.delete("invites/" + previousCode)};
    }

    /** Join: member in the new house, its count +1, user moved, and the old empty house removed. */
    static Write[] join(String uid, String houseId, String code, String oldHouseId) {
        return new Write[] {
            Write.create("houses/" + houseId + "/members/" + uid,
                    Fields.of().str("displayName", nameOf(uid)).str("inviteCode", code))
                    .serverTime("joinedAt"),
            Write.merge("houses/" + houseId, Fields.of().str("memberMutationId", uid)).increment("memberCount", 1),
            Write.merge("users/" + uid, Fields.of().str("currentHouseId", houseId)).serverTime("updatedAt"),
            Write.delete("houses/" + oldHouseId + "/members/" + uid),
            Write.delete("houses/" + oldHouseId)
        };
    }

    /** Leave: own member removed, count -1, and a new one-person house for the leaver. */
    static Write[] leave(String uid, String houseId, String newHouseId) {
        return new Write[] {
            Write.delete("houses/" + houseId + "/members/" + uid),
            Write.merge("houses/" + houseId, Fields.of().str("memberMutationId", uid)).increment("memberCount", -1),
            newHouse(newHouseId, uid),
            newMember(newHouseId, uid, nameOf(uid)),
            Write.merge("users/" + uid, Fields.of().str("currentHouseId", newHouseId)).serverTime("updatedAt")
        };
    }

    /** Remove: the member document and the count -1 (the owner's request). */
    static Write[] removeMember(String houseId, String uid) {
        return new Write[] {
            Write.delete("houses/" + houseId + "/members/" + uid),
            Write.merge("houses/" + houseId, Fields.of().str("memberMutationId", uid)).increment("memberCount", -1)
        };
    }

    static Write startDeleting(String parent, String actor) {
        return Write.merge(parent, Fields.of().bool("deleting", true).str("updatedBy", actor))
                .serverTime("updatedAt");
    }

    static Write[] deleteRecord(String parent, String collection, String record, String actor) {
        return new Write[] {
            Write.delete(parent + "/" + collection + "/" + record),
            Write.merge(parent, Fields.of().str("recordMutationId", record).str("updatedBy", actor))
                    .increment("recordCount", -1).serverTime("updatedAt")
        };
    }

    static void assertAllowed(Response response) {
        assertEquals(response.toString(), OK, response.status());
    }

    static void assertDenied(Response response) {
        assertEquals(response.toString(), DENIED, response.status());
    }
}
