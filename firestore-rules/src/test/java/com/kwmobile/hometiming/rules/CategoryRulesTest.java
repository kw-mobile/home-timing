package com.kwmobile.hometiming.rules;

import org.junit.Before;
import org.junit.Test;

public class CategoryRulesTest extends RulesTestBase {
    private static final String PATH = "houses/house-a/categoryCatalogs/item";
    private static final Auth OWNER = Auth.user("alice");
    private static final Auth MEMBER = Auth.user("bob");
    private static final Auth OUTSIDER = Auth.user("carol");
    private static final String BATHROOM = "default_bathroom";
    private static final String KITCHEN = "default_kitchen";
    private static final String OTHER = "default_other";

    @Before public void prepare() {
        seedHouse("house-a", 0, "alice", "bob");
    }

    @Test public void membersCanShareRenameAndDeleteButOutsidersCannot() {
        assertDenied(firestore.commit(OUTSIDER, catalog("carol", 1, "INIT", "", "",
                labels("욕실"), Fields.of())));
        assertAllowed(firestore.commit(OWNER, catalog("alice", 1, "INIT", "", "",
                labels("욕실"), Fields.of())));
        assertAllowed(firestore.get(MEMBER, PATH));
        assertDenied(firestore.get(OUTSIDER, PATH));
        assertDenied(firestore.commit(OUTSIDER, catalog("carol", 2, "RENAME", BATHROOM, "",
                labels("화장실"), Fields.of())));
        assertAllowed(firestore.commit(MEMBER, catalog("bob", 2, "RENAME", BATHROOM, "",
                labels("화장실"), Fields.of())));
        assertAllowed(firestore.commit(MEMBER, catalog("bob", 3, "DELETE", BATHROOM, KITCHEN,
                Fields.of().str(KITCHEN, "주방").str(OTHER, "기타"), Fields.of().str(BATHROOM, KITCHEN))));
        assertDenied(firestore.commit(OWNER, Write.delete(PATH)));
    }

    @Test public void rejectsDuplicateNameStaleRevisionAndInvalidReplacement() {
        assertDenied(firestore.commit(OWNER, catalog("alice", 1, "INIT", "", "",
                labels("변조"), Fields.of())));
        assertAllowed(firestore.commit(OWNER, catalog("alice", 1, "INIT", "", "",
                labels("욕실"), Fields.of())));
        assertDenied(firestore.commit(MEMBER, catalog("bob", 2, "RENAME", BATHROOM, "",
                labels("주방"), Fields.of())));
        assertDenied(firestore.commit(MEMBER, catalog("bob", 1, "RENAME", BATHROOM, "",
                labels("화장실"), Fields.of())));
        assertDenied(firestore.commit(MEMBER, catalog("bob", 2, "DELETE", BATHROOM, BATHROOM,
                Fields.of().str(KITCHEN, "주방").str(OTHER, "기타"), Fields.of().str(BATHROOM, BATHROOM))));
    }

    @Test public void rejectsDeletingTheLastCategoryAndRewritingRedirects() {
        assertAllowed(firestore.commit(OWNER, catalog("alice", 1, "INIT", "", "",
                labels("욕실"), Fields.of())));
        assertAllowed(firestore.commit(OWNER, catalog("alice", 2, "DELETE", BATHROOM, KITCHEN,
                Fields.of().str(KITCHEN, "주방").str(OTHER, "기타"), Fields.of().str(BATHROOM, KITCHEN))));
        assertDenied(firestore.commit(OWNER, catalog("alice", 3, "DELETE", KITCHEN, OTHER,
                Fields.of().str(OTHER, "기타"), Fields.of().str(BATHROOM, OTHER).str(KITCHEN, OTHER))));
        assertAllowed(firestore.commit(OWNER, catalog("alice", 3, "DELETE", KITCHEN, OTHER,
                Fields.of().str(OTHER, "기타"), Fields.of().str(BATHROOM, KITCHEN).str(KITCHEN, OTHER))));
        assertDenied(firestore.commit(OWNER, catalog("alice", 4, "DELETE", OTHER, BATHROOM,
                Fields.of(), Fields.of().str(BATHROOM, KITCHEN).str(KITCHEN, OTHER).str(OTHER, BATHROOM))));
    }

    private static Fields labels(String bathroom) {
        return Fields.of().str(BATHROOM, bathroom).str(KITCHEN, "주방").str(OTHER, "기타");
    }

    private static Write catalog(String actor, int revision, String operation, String source,
                                 String target, Fields labels, Fields redirects) {
        return Write.set(PATH, Fields.of().map("labels", labels).map("redirects", redirects)
                .num("revision", revision).str("operation", operation).str("source", source)
                .str("target", target).str("updatedBy", actor)).serverTime("updatedAt");
    }
}
