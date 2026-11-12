package com.kwmobile.hometiming.data.firestore;

import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.HistoryEntry;
import com.kwmobile.hometiming.domain.Item;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts items and history between domain models and Firestore fields (docs/DATA_MODEL.md).
 *
 * <p>Pure Java so the field sets are unit-tested against what firestore.rules v1 allows. The
 * repository passes {@code FieldValue.serverTimestamp()} as {@code now}.
 */
public final class ItemDocuments {
    private ItemDocuments() {
    }

    /**
     * Every field of an item document. {@code createdAt} is the server time for a new item and the
     * stored value for an update, so the rules see it unchanged. Empty optional text is omitted.
     */
    public static Map<String, Object> itemFields(Item item, String updatedBy, Object createdAt, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Records.COUNT, 0);
        fields.put(Schema.Records.DELETING, false);
        fields.put(Schema.Items.NAME, item.name);
        fields.put(Schema.Items.CATEGORY, item.category);
        fields.put(Schema.Items.START_DATE, DateRules.format(item.startDate));
        fields.put(Schema.Items.CYCLE_DAYS, item.cycleDays);
        fields.put(Schema.Items.LAST_REPLACED_DATE, DateRules.format(item.lastReplacedDate));
        fields.put(Schema.Items.NEXT_DUE_DATE, DateRules.format(item.nextDueDate));
        fields.put(Schema.Items.OCCURRENCE_ID, item.occurrenceId);
        fields.put(Schema.Items.REVISION, item.revision);
        putOptional(fields, Schema.Items.PRODUCT_NAME, item.productName);
        putOptional(fields, Schema.Items.LOCATION, item.location);
        putOptional(fields, Schema.Items.MEMO, item.memo);
        fields.put(Schema.Items.CREATED_BY, item.createdBy);
        fields.put(Schema.Items.UPDATED_BY, Objects.requireNonNull(updatedBy, "updatedBy"));
        fields.put(Schema.Items.CREATED_AT, Objects.requireNonNull(createdAt, "createdAt"));
        fields.put(Schema.Items.UPDATED_AT, now);
        return fields;
    }

    public static Map<String, Object> historyFields(HistoryEntry entry, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.History.REPLACED_DATE, DateRules.format(entry.replacedDate));
        fields.put(Schema.History.PREVIOUS_DUE_DATE, DateRules.format(entry.previousDueDate));
        fields.put(Schema.History.CYCLE_DAYS_USED, entry.cycleDaysUsed);
        fields.put(Schema.History.COMPLETED_BY, entry.completedBy);
        fields.put(Schema.History.COMPLETED_BY_NAME, entry.completedByName);
        fields.put(Schema.History.CREATED_AT, now);
        return fields;
    }

    /**
     * Reads a stored item. Returns null for a document that does not have the v1 shape, so one bad
     * document does not break a whole list.
     */
    public static Item toItem(String id, Map<String, Object> data) {
        try {
            if (Boolean.TRUE.equals(data.get(Schema.Records.DELETING))) {
                return null;
            }
            return new Item(id,
                    text(data, Schema.Items.NAME),
                    text(data, Schema.Items.CATEGORY),
                    date(data, Schema.Items.START_DATE),
                    whole(data, Schema.Items.CYCLE_DAYS),
                    date(data, Schema.Items.LAST_REPLACED_DATE),
                    date(data, Schema.Items.NEXT_DUE_DATE),
                    text(data, Schema.Items.OCCURRENCE_ID),
                    whole(data, Schema.Items.REVISION),
                    optional(data, Schema.Items.PRODUCT_NAME),
                    optional(data, Schema.Items.LOCATION),
                    optional(data, Schema.Items.MEMO),
                    text(data, Schema.Items.CREATED_BY),
                    !(data.get(Schema.Records.COUNT) instanceof Number)
                            || ((Number) data.get(Schema.Records.COUNT)).longValue() > 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads a stored history entry; null when malformed. */
    public static HistoryEntry toHistory(String occurrenceId, Map<String, Object> data) {
        try {
            return new HistoryEntry(occurrenceId,
                    date(data, Schema.History.REPLACED_DATE),
                    date(data, Schema.History.PREVIOUS_DUE_DATE),
                    whole(data, Schema.History.CYCLE_DAYS_USED),
                    text(data, Schema.History.COMPLETED_BY),
                    text(data, Schema.History.COMPLETED_BY_NAME));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void putOptional(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isEmpty()) {
            fields.put(key, value);
        }
    }

    private static String text(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key);
        }
        return (String) value;
    }

    private static String optional(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof String ? (String) value : "";
    }

    private static LocalDate date(Map<String, Object> data, String key) {
        return DateRules.parse(text(data, key));
    }

    // Firestore returns integers as Long.
    private static int whole(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key);
        }
        return Math.toIntExact(((Number) value).longValue());
    }
}
