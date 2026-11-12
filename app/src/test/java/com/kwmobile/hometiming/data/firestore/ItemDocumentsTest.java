package com.kwmobile.hometiming.data.firestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.HistoryEntry;
import com.kwmobile.hometiming.domain.Item;
import com.kwmobile.hometiming.domain.ItemDraft;
import com.kwmobile.hometiming.domain.ItemSchedule;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ItemDocumentsTest {
    private static final Object CREATED = new Object();
    private static final Object NOW = new Object();

    // The exact key lists firestore.rules v1 allows for items and history.
    private static final Set<String> REQUIRED_ITEM_KEYS = new HashSet<>(Arrays.asList(
            "name", "category", "startDate", "cycleDays", "lastReplacedDate", "nextDueDate",
            "occurrenceId", "revision", "createdBy", "updatedBy", "createdAt", "updatedAt", "recordCount", "deleting"));
    private static final Set<String> HISTORY_KEYS = new HashSet<>(Arrays.asList(
            "replacedDate", "previousDueDate", "cycleDaysUsed", "completedBy", "completedByName", "createdAt"));

    private static Item item(String memo) {
        return ItemSchedule.register("filter",
                new ItemDraft("샤워기 필터", null, LocalDate.of(2026, 11, 1), 60, "", "", memo), "alice", "occ-1");
    }

    @Test
    public void itemFieldsMatchTheRules() {
        Map<String, Object> fields = ItemDocuments.itemFields(item(""), "bob", CREATED, NOW);

        assertEquals(REQUIRED_ITEM_KEYS, fields.keySet());
        assertEquals("2026-11-01", fields.get("startDate"));
        assertEquals("2026-12-31", fields.get("nextDueDate"));
        assertEquals(60, fields.get("cycleDays"));
        assertEquals("ETC", fields.get("category"));
        assertEquals("alice", fields.get("createdBy"));
        assertEquals("bob", fields.get("updatedBy"));
        assertSame(CREATED, fields.get("createdAt"));
        assertSame(NOW, fields.get("updatedAt"));
    }

    @Test
    public void optionalTextIsWrittenOnlyWhenSet() {
        Map<String, Object> fields = ItemDocuments.itemFields(item("서랍"), "alice", CREATED, NOW);

        assertEquals("서랍", fields.get("memo"));
        assertTrue(!fields.containsKey("productName") && !fields.containsKey("location"));
    }

    @Test
    public void itemRoundTripsThroughStoredValues() {
        Map<String, Object> stored = new HashMap<>(ItemDocuments.itemFields(item("서랍"), "alice", CREATED, NOW));
        // Firestore hands integers back as Long.
        stored.put("cycleDays", 60L);
        stored.put("revision", 1L);

        Item read = ItemDocuments.toItem("filter", stored);

        assertEquals("filter", read.id);
        assertEquals(LocalDate.of(2026, 12, 31), read.nextDueDate);
        assertEquals(60, read.cycleDays);
        assertEquals("서랍", read.memo);
        assertEquals("", read.location);
    }

    @Test
    public void malformedItemIsSkipped() {
        Map<String, Object> stored = new HashMap<>(ItemDocuments.itemFields(item(""), "alice", CREATED, NOW));
        stored.put("nextDueDate", "2026-1-5");
        assertNull(ItemDocuments.toItem("filter", stored));

        stored = new HashMap<>(ItemDocuments.itemFields(item(""), "alice", CREATED, NOW));
        stored.remove("occurrenceId");
        assertNull(ItemDocuments.toItem("filter", stored));
    }

    @Test
    public void historyFieldsMatchTheRulesAndRoundTrip() {
        HistoryEntry entry = ItemSchedule.historyOf(item(""), LocalDate.of(2027, 1, 3), "bob", "밥");

        Map<String, Object> fields = ItemDocuments.historyFields(entry, NOW);
        assertEquals(HISTORY_KEYS, fields.keySet());
        assertEquals("2027-01-03", fields.get("replacedDate"));
        assertEquals("2026-12-31", fields.get("previousDueDate"));

        Map<String, Object> stored = new HashMap<>(fields);
        stored.put("cycleDaysUsed", 60L);
        HistoryEntry read = ItemDocuments.toHistory("occ-1", stored);
        assertEquals("occ-1", read.occurrenceId);
        assertEquals(LocalDate.of(2027, 1, 3), read.replacedDate);
        assertEquals("밥", read.completedByName);
    }
}
