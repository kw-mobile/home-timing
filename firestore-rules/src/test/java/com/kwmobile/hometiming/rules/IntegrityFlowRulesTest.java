package com.kwmobile.hometiming.rules;

import static com.kwmobile.hometiming.rules.FirestoreEmulator.NOT_FOUND;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

/** Cross-document user flows, including interrupted deletion and hostile clients. */
public class IntegrityFlowRulesTest extends RulesTestBase {
    private static final Auth OWNER = Auth.user("alice");
    private static final Auth MEMBER = Auth.user("bob");
    private static final String HOUSE = "home";
    private static final String ITEM = "houses/home/items/filter";

    @Before
    public void seedHome() {
        seedHouse(HOUSE, 0, "alice", "bob");
    }

    private static Write[] register(String id) {
        return createItem(HOUSE, id, itemFields("alice", today(), today(), 30, "first", 1));
    }

    private static Write[] complete() {
        return complete("first", "second", 2, 1);
    }

    private static Write[] complete(String before, String after, int revision, int count) {
        return new Write[] {
            Write.merge(ITEM, Fields.of().str("occurrenceId", after).num("revision", revision)
                    .num("recordCount", count).str("recordMutationId", before).str("updatedBy", "bob"))
                    .serverTime("updatedAt"),
            Write.create(ITEM + "/history/" + before, Fields.of().str("replacedDate", today())
                    .str("previousDueDate", daysFromToday(30)).num("cycleDaysUsed", 30)
                    .str("completedBy", "bob").str("completedByName", nameOf("bob")))
                    .serverTime("createdAt")
        };
    }

    private static Write[] remove() {
        return new Write[] {Write.delete(ITEM), Write.merge("houses/home",
                Fields.of().str("itemMutationId", "filter")).increment("itemCount", -1)};
    }

    @Test
    public void registerRejectsForgedCountsAndSharedMutationTargets() {
        assertAllowed(firestore.commit(OWNER, register("filter")));
        assertDenied(firestore.commit(OWNER, Write.merge("houses/home",
                Fields.of().str("itemMutationId", "filter")).increment("itemCount", -1)));
        assertDenied(firestore.commit(OWNER, register("one")[0], register("two")[0], register("one")[1]));
        assertDenied(firestore.commit(OWNER, Write.merge("houses/home",
                Fields.of().str("memberMutationId", "bob")).increment("memberCount", -1)));
        // The rejected count forgery must not allow deleting the populated house.
        assertAllowed(firestore.get(MEMBER, ITEM));
    }

    @Test
    public void sameDayCompletionPreservesActualDateWhenEditingStartDate() {
        assertAllowed(firestore.commit(OWNER, register("filter")));
        assertAllowed(firestore.commit(MEMBER, complete()));
        assertAllowed(firestore.commit(OWNER, Write.merge(ITEM, Fields.of()
                .str("startDate", daysFromToday(-1)).num("revision", 3).str("updatedBy", "alice"))
                .serverTime("updatedAt")));
        // Moving the start date cannot also fabricate a different actual replacement.
        assertDenied(firestore.commit(OWNER, Write.merge(ITEM, Fields.of()
                .str("lastReplacedDate", daysFromToday(-1)).str("nextDueDate", daysFromToday(29))
                .num("revision", 4).str("updatedBy", "alice")).serverTime("updatedAt")));
        assertDenied(firestore.commit(OWNER, Write.delete(ITEM + "/history/first")));
    }

    @Test
    public void deleteBlocksConcurrentCompletionAndResumesAfterInterruption() {
        assertAllowed(firestore.commit(OWNER, register("filter")));
        assertAllowed(firestore.commit(MEMBER, complete()));
        assertAllowed(firestore.commit(MEMBER, complete("second", "third", 3, 2)));
        assertDenied(firestore.commit(OWNER, remove()));
        assertAllowed(firestore.commit(OWNER, startDeleting(ITEM, "alice")));
        assertDenied(firestore.commit(OWNER, remove())); // History has not been drained yet.
        assertDenied(firestore.commit(MEMBER, Write.merge(ITEM, Fields.of().str("name", "changed")
                .str("updatedBy", "bob")).serverTime("updatedAt")));
        assertDenied(firestore.commit(OWNER, Write.merge(ITEM, Fields.of().bool("deleting", false)
                .str("updatedBy", "alice")).serverTime("updatedAt")));
        // A fresh attempt sees the deletion marker and drains its remaining record.
        assertDenied(firestore.commit(MEMBER, complete("third", "fourth", 4, 3)));
        assertDenied(firestore.commit(OWNER, Write.delete(ITEM + "/history/first"),
                Write.delete(ITEM + "/history/second"), deleteRecord(ITEM, "history", "first", "alice")[1]));
        assertAllowed(firestore.commit(OWNER, deleteRecord(ITEM, "history", "first", "alice")));
        assertDenied(firestore.commit(OWNER, remove()));
        assertAllowed(firestore.commit(OWNER, deleteRecord(ITEM, "history", "second", "alice")));
        assertAllowed(firestore.commit(OWNER, remove()));
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), ITEM + "/history/first").status());
        assertEquals(NOT_FOUND, firestore.get(Auth.admin(), ITEM).status());
    }

    @Test
    public void legacyDataRemainsReadableButCannotSelfInitializeOrDissolve() {
        assertAllowed(firestore.commit(OWNER, register("filter")));
        assertAllowed(firestore.commit(Auth.admin(), Write.merge("houses/home", Fields.of())
                .remove("integrityVersion")));
        assertAllowed(firestore.get(OWNER, ITEM));
        assertDenied(firestore.commit(OWNER, Write.merge(ITEM, Fields.of().str("name", "changed")
                .str("updatedBy", "alice")).serverTime("updatedAt")));
        assertDenied(firestore.commit(OWNER, Write.merge("houses/home", Fields.of().num("integrityVersion", 2))));
        assertAllowed(firestore.commit(Auth.admin(), Write.merge("houses/home", Fields.of().num("integrityVersion", 2)),
                Write.merge(ITEM, Fields.of()).remove("recordCount").remove("deleting")));
        assertDenied(firestore.commit(OWNER, Write.merge(ITEM, Fields.of().num("recordCount", 0)
                .bool("deleting", false).str("updatedBy", "alice")).serverTime("updatedAt")));
    }
}
