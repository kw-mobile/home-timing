package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/** items and history (docs/DATA_MODEL.md 용품 등록·수정·완료·삭제, #20 교체일 하한). */
public class ItemRulesTest extends RulesTestBase {
    private static final String HOUSE = "house-a";
    private static final Auth OWNER = Auth.user("alice");
    private static final Auth MEMBER = Auth.user("bob");
    private static final Auth OUTSIDER = Auth.user("carol");

    @Before
    public void seedHouses() {
        seedHouse(HOUSE, 1, "alice", "bob");
        seedHouse("house-c", 0, "carol");
        // bob's item, started 30 days ago with a 60-day cycle.
        seedItem(HOUSE, "filter", "bob", daysFromToday(-30), 60, "occ-1", 1);
    }

    // ---- Read ----

    @Test
    public void membersReadItemsOutsidersDoNot() {
        assertAllowed(firestore.get(OWNER, itemPath(HOUSE, "filter")));
        assertDenied(firestore.get(OUTSIDER, itemPath(HOUSE, "filter")));
        assertDenied(firestore.get(Auth.signedOut(), itemPath(HOUSE, "filter")));
    }

    // ---- Create ----

    @Test
    public void memberRegistersItemWithCountIncrement() {
        assertAllowed(firestore.commit(OWNER, createItem(HOUSE, "brush",
                itemFields("alice", today(), today(), 90, "occ-b1", 1).str("memo", "욕실 서랍"))));
    }

    @Test
    public void registrationNeedsTheCountIncrement() {
        Write item = createItem(HOUSE, "brush", itemFields("alice", today(), today(), 90, "occ-b1", 1))[0];

        assertDenied(firestore.commit(OWNER, item));
    }

    @Test
    public void outsiderCannotRegister() {
        assertDenied(firestore.commit(OUTSIDER, createItem(HOUSE, "brush",
                itemFields("carol", today(), today(), 90, "occ-b1", 1))));
    }

    @Test
    public void registrantMustBeTheCaller() {
        assertDenied(firestore.commit(MEMBER, createItem(HOUSE, "brush",
                itemFields("alice", today(), today(), 90, "occ-b1", 1).str("updatedBy", "bob"))));
    }

    @Test
    public void cycleMustBeOneTo730Days() {
        assertAllowed(firestore.commit(OWNER, createItem(HOUSE, "one",
                itemFields("alice", today(), today(), 1, "occ-1d", 1))));
        assertAllowed(firestore.commit(OWNER, createItem(HOUSE, "max",
                itemFields("alice", today(), today(), 730, "occ-730", 1))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "zero",
                itemFields("alice", today(), today(), 1, "occ-0", 1).num("cycleDays", 0))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "long",
                itemFields("alice", today(), today(), 731, "occ-731", 1))));
    }

    @Test
    public void startDateCannotBeAfterTodayInSeoul() {
        String tomorrow = daysFromToday(1);

        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "future",
                itemFields("alice", tomorrow, tomorrow, 30, "occ-f", 1))));
        assertAllowed(firestore.commit(OWNER, createItem(HOUSE, "past",
                itemFields("alice", "2025-01-31", "2025-01-31", 30, "occ-p", 1))));
    }

    @Test
    public void nextDueDateMustBeLastReplacedPlusCycle() {
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "wrong",
                itemFields("alice", today(), today(), 30, "occ-w", 1).str("nextDueDate", daysFromToday(31)))));
        // Across a leap day: 2024-02-28 + 2 days = 2024-03-01.
        assertAllowed(firestore.commit(OWNER, createItem(HOUSE, "leap",
                itemFields("alice", "2024-02-28", "2024-02-28", 2, "occ-l", 1))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "noleap",
                itemFields("alice", "2024-02-28", "2024-02-28", 2, "occ-nl", 1).str("nextDueDate", "2024-02-30"))));
    }

    @Test
    public void malformedFieldsAreRejected() {
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "date",
                itemFields("alice", today(), today(), 30, "occ-d", 1)
                        .str("startDate", "2026-1-5").str("lastReplacedDate", "2026-1-5"))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "blank",
                itemFields("alice", today(), today(), 30, "occ-n", 1).str("name", "   "))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "longname",
                itemFields("alice", today(), today(), 30, "occ-ln", 1).str("name", "가".repeat(61)))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "extra",
                itemFields("alice", today(), today(), 30, "occ-x", 1).str("photoPath", "/x.jpg"))));
        assertDenied(firestore.commit(OWNER, createItem(HOUSE, "rev",
                itemFields("alice", today(), today(), 30, "occ-r", 2))));
    }

    // ---- Edit ----

    @Test
    public void displayEditKeepsRevision() {
        assertAllowed(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"),
                Fields.of().str("name", "욕실 샤워기 필터").str("updatedBy", "alice")).serverTime("updatedAt")));
        assertDenied(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"),
                Fields.of().str("memo", "x").num("revision", 2).str("updatedBy", "alice")).serverTime("updatedAt")));
    }

    @Test
    public void scheduleEditBumpsRevisionOnce() {
        String start = daysFromToday(-30);

        assertDenied(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"), Fields.of()
                .num("cycleDays", 90).str("nextDueDate", plusDays(start, 90)).str("updatedBy", "alice"))
                .serverTime("updatedAt")));
        assertAllowed(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"), Fields.of()
                .num("cycleDays", 90).str("nextDueDate", plusDays(start, 90)).num("revision", 2)
                .str("updatedBy", "alice")).serverTime("updatedAt")));
    }

    @Test
    public void fullDocumentEditLikeTheRepositoryIsAllowed() {
        // ItemRepository.update rewrites the whole document with the stored createdAt.
        String newStart = daysFromToday(-40);
        Fields edited = itemFields("bob", newStart, newStart, 60, "occ-1", 2)
                .str("updatedBy", "alice").time("createdAt", SEEDED_AT);

        assertAllowed(firestore.commit(OWNER, Write.set(itemPath(HOUSE, "filter"), edited).serverTime("updatedAt")));
    }

    @Test
    public void fullDocumentEditCannotResetCreatedAt() {
        Fields edited = itemFields("bob", daysFromToday(-30), daysFromToday(-30), 60, "occ-1", 1)
                .str("updatedBy", "alice");

        assertDenied(firestore.commit(OWNER, Write.set(itemPath(HOUSE, "filter"), edited)
                .serverTime("createdAt").serverTime("updatedAt")));
    }

    @Test
    public void ownershipAndOccurrenceCannotBeRewrittenByEdit() {
        assertDenied(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"),
                Fields.of().str("createdBy", "alice").str("updatedBy", "alice")).serverTime("updatedAt")));
        assertDenied(firestore.commit(OWNER, Write.merge(itemPath(HOUSE, "filter"),
                Fields.of().str("occurrenceId", "occ-forged").num("revision", 2).str("updatedBy", "alice"))
                .serverTime("updatedAt")));
        assertDenied(firestore.commit(OUTSIDER, Write.merge(itemPath(HOUSE, "filter"),
                Fields.of().str("name", "x").str("updatedBy", "carol")).serverTime("updatedAt")));
    }

    // ---- Complete ----

    private static Write[] complete(String actor, String replacedDate, String finishedOccurrence,
                                    String nextOccurrence, int nextRevision, String completedByName) {
        String start = daysFromToday(-30);
        return new Write[] {
            Write.create(historyPath(HOUSE, "filter", finishedOccurrence), Fields.of()
                    .str("replacedDate", replacedDate)
                    .str("previousDueDate", plusDays(start, 60))
                    .num("cycleDaysUsed", 60)
                    .str("completedBy", actor)
                    .str("completedByName", completedByName))
                    .serverTime("createdAt"),
            Write.merge(itemPath(HOUSE, "filter"), Fields.of()
                    .str("lastReplacedDate", replacedDate)
                    .str("nextDueDate", plusDays(replacedDate, 60))
                    .str("occurrenceId", nextOccurrence)
                    .num("revision", nextRevision)
                    .str("updatedBy", actor))
                    .serverTime("updatedAt")
        };
    }

    @Test
    public void anyMemberCompletesWithHistory() {
        assertAllowed(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 2, nameOf("alice"))));
        assertAllowed(firestore.get(MEMBER, historyPath(HOUSE, "filter", "occ-1")));
    }

    @Test
    public void sameOccurrenceCannotBeCompletedTwice() {
        assertAllowed(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 2, nameOf("alice"))));

        // A second device still showing occ-1 retries the same completion.
        assertDenied(firestore.commit(MEMBER, complete("bob", today(), "occ-1", "occ-3", 2, nameOf("bob"))));
    }

    @Test
    public void completionNeedsHistoryAndNewOccurrence() {
        Write[] writes = complete("alice", today(), "occ-1", "occ-2", 2, nameOf("alice"));

        assertDenied(firestore.commit(OWNER, writes[1]));
        assertDenied(firestore.commit(OWNER, writes[0]));
        assertDenied(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-1", 2, nameOf("alice"))));
        assertDenied(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 3, nameOf("alice"))));
    }

    @Test
    public void replacedDateCannotGoBeforePreviousOrAfterToday() {
        assertDenied(firestore.commit(OWNER,
                complete("alice", daysFromToday(-31), "occ-1", "occ-2", 2, nameOf("alice"))));
        assertDenied(firestore.commit(OWNER,
                complete("alice", daysFromToday(1), "occ-1", "occ-2", 2, nameOf("alice"))));
        // Same day as the previous replacement is allowed (#20).
        assertAllowed(firestore.commit(OWNER,
                complete("alice", daysFromToday(-30), "occ-1", "occ-2", 2, nameOf("alice"))));
    }

    @Test
    public void historyMustMatchTheCompletingMember() {
        assertDenied(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 2, "가짜 이름")));
        assertDenied(firestore.commit(OWNER, complete("bob", today(), "occ-1", "occ-2", 2, nameOf("bob"))));
        assertDenied(firestore.commit(OUTSIDER, complete("carol", today(), "occ-1", "occ-2", 2, nameOf("carol"))));
    }

    @Test
    public void historyCannotBeEdited() {
        assertAllowed(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 2, nameOf("alice"))));

        assertDenied(firestore.commit(OWNER, Write.merge(historyPath(HOUSE, "filter", "occ-1"),
                Fields.of().str("replacedDate", daysFromToday(-1)))));
    }

    // ---- Delete ----

    private static Write[] deleteFilter() {
        return new Write[] {
            Write.delete(itemPath(HOUSE, "filter")),
            Write.merge("houses/" + HOUSE, Fields.of()).increment("itemCount", -1)
        };
    }

    @Test
    public void registrantDeletesWithHistoryAndCount() {
        assertAllowed(firestore.commit(OWNER, complete("alice", today(), "occ-1", "occ-2", 2, nameOf("alice"))));

        assertAllowed(firestore.commit(MEMBER,
                Write.delete(historyPath(HOUSE, "filter", "occ-1")), deleteFilter()[0], deleteFilter()[1]));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), itemPath(HOUSE, "filter")).status());
    }

    @Test
    public void ownerMayDeleteOthersItems() {
        assertAllowed(firestore.commit(OWNER, deleteFilter()));
    }

    @Test
    public void otherMemberCannotDelete() {
        seedItem(HOUSE, "alices", "alice", today(), 30, "occ-a", 1);

        assertDenied(firestore.commit(MEMBER,
                Write.delete(itemPath(HOUSE, "alices")),
                Write.merge("houses/" + HOUSE, Fields.of()).increment("itemCount", -1)));
    }

    @Test
    public void deleteNeedsTheCountDecrement() {
        assertDenied(firestore.commit(OWNER, deleteFilter()[0]));
    }

    @Test
    public void otherMemberCannotDeleteHistory() {
        seedItem(HOUSE, "alices", "alice", today(), 30, "occ-a", 1);
        firestore.seed(historyPath(HOUSE, "alices", "occ-0"), Fields.of()
                .str("replacedDate", today()).str("previousDueDate", today()).num("cycleDaysUsed", 30)
                .str("completedBy", "alice").str("completedByName", nameOf("alice")).time("createdAt", SEEDED_AT));

        assertDenied(firestore.delete(MEMBER, historyPath(HOUSE, "alices", "occ-0")));
        assertAllowed(firestore.delete(OWNER, historyPath(HOUSE, "alices", "occ-0")));
    }
}
