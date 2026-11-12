package com.kwmobile.hometiming.data.firestore;

import com.kwmobile.hometiming.domain.Chore;
import com.kwmobile.hometiming.domain.ChoreLog;
import com.kwmobile.hometiming.domain.DateRules;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Converts chores and logs between domain models and Firestore fields (docs/DATA_MODEL.md). Pure
 * Java so the field sets are unit-tested against firestore.rules; the repository passes
 * {@code FieldValue.serverTimestamp()} as {@code now}.
 */
public final class ChoreDocuments {
    private ChoreDocuments() {
    }

    /** The log id of a completion: one per occurrence, so a second completion cannot be created. */
    public static String doneLogId(String occurrenceId) {
        return "done_" + occurrenceId;
    }

    /** The log id of a move: tied to the revision it produced, so the rules can require it. */
    public static String movedLogId(String occurrenceId, int revision) {
        return "moved_" + occurrenceId + "_" + revision;
    }

    /**
     * Every field of a chore document. {@code createdAt} is the server time for a new chore and the
     * stored value for an update. Unset optional values are omitted.
     */
    public static Map<String, Object> choreFields(Chore chore, String updatedBy, Object createdAt, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Records.COUNT, 0);
        fields.put(Schema.Records.DELETING, false);
        fields.put(Schema.Chores.NAME, chore.name);
        fields.put(Schema.Chores.TYPE, chore.type);
        fields.put(Schema.Chores.WEATHER_SENSITIVE, chore.weatherSensitive);
        fields.put(Schema.Chores.WEEKDAY, chore.weekday);
        fields.put(Schema.Chores.PLANNED_DUE_DATE, DateRules.format(chore.plannedDueDate));
        fields.put(Schema.Chores.NEXT_DUE_DATE, DateRules.format(chore.nextDueDate));
        fields.put(Schema.Chores.OCCURRENCE_ID, chore.occurrenceId);
        fields.put(Schema.Chores.REVISION, chore.revision);
        if (chore.dismissedOccurrenceId != null) {
            fields.put(Schema.Chores.DISMISSED_OCCURRENCE_ID, chore.dismissedOccurrenceId);
        }
        if (chore.lastDoneDate != null) {
            fields.put(Schema.Chores.LAST_DONE_DATE, DateRules.format(chore.lastDoneDate));
        }
        if (!chore.location.isEmpty()) {
            fields.put(Schema.Chores.LOCATION, chore.location);
        }
        if (!chore.memo.isEmpty()) {
            fields.put(Schema.Chores.MEMO, chore.memo);
        }
        fields.put(Schema.Chores.CREATED_BY, chore.createdBy);
        fields.put(Schema.Chores.UPDATED_BY, Objects.requireNonNull(updatedBy, "updatedBy"));
        fields.put(Schema.Chores.CREATED_AT, Objects.requireNonNull(createdAt, "createdAt"));
        fields.put(Schema.Chores.UPDATED_AT, now);
        return fields;
    }

    public static Map<String, Object> doneLogFields(String occurrenceId, LocalDate doneDate, String uid, String name,
                                                    Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Logs.ACTION, Schema.Logs.ACTION_DONE);
        fields.put(Schema.Logs.OCCURRENCE_ID, occurrenceId);
        fields.put(Schema.Logs.DATE, DateRules.format(doneDate));
        fields.put(Schema.Logs.ACTOR_ID, uid);
        fields.put(Schema.Logs.ACTOR_NAME, name);
        fields.put(Schema.Logs.CREATED_AT, now);
        return fields;
    }

    public static Map<String, Object> movedLogFields(String occurrenceId, LocalDate from, LocalDate to, String reason,
                                                     String uid, String name, Object now) {
        Map<String, Object> fields = new HashMap<>();
        fields.put(Schema.Logs.ACTION, Schema.Logs.ACTION_RESCHEDULED);
        fields.put(Schema.Logs.OCCURRENCE_ID, occurrenceId);
        fields.put(Schema.Logs.DATE, DateRules.format(to));
        fields.put(Schema.Logs.FROM_DATE, DateRules.format(from));
        fields.put(Schema.Logs.REASON, reason);
        fields.put(Schema.Logs.ACTOR_ID, uid);
        fields.put(Schema.Logs.ACTOR_NAME, name);
        fields.put(Schema.Logs.CREATED_AT, now);
        return fields;
    }

    /** Reads a stored chore; null when it does not have the expected shape. */
    public static Chore toChore(String id, Map<String, Object> data) {
        try {
            if (Boolean.TRUE.equals(data.get(Schema.Records.DELETING))) {
                return null;
            }
            Object sensitive = data.get(Schema.Chores.WEATHER_SENSITIVE);
            if (!(sensitive instanceof Boolean)) {
                return null;
            }
            String lastDone = optional(data, Schema.Chores.LAST_DONE_DATE);
            return new Chore(id,
                    text(data, Schema.Chores.NAME),
                    text(data, Schema.Chores.TYPE),
                    (Boolean) sensitive,
                    whole(data, Schema.Chores.WEEKDAY),
                    DateRules.parse(text(data, Schema.Chores.PLANNED_DUE_DATE)),
                    DateRules.parse(text(data, Schema.Chores.NEXT_DUE_DATE)),
                    text(data, Schema.Chores.OCCURRENCE_ID),
                    whole(data, Schema.Chores.REVISION),
                    optional(data, Schema.Chores.DISMISSED_OCCURRENCE_ID),
                    lastDone == null ? null : DateRules.parse(lastDone),
                    optional(data, Schema.Chores.LOCATION),
                    optional(data, Schema.Chores.MEMO),
                    text(data, Schema.Chores.CREATED_BY));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads a stored log; null when malformed. */
    public static ChoreLog toLog(Map<String, Object> data) {
        try {
            String from = optional(data, Schema.Logs.FROM_DATE);
            return new ChoreLog(
                    text(data, Schema.Logs.ACTION),
                    text(data, Schema.Logs.OCCURRENCE_ID),
                    DateRules.parse(text(data, Schema.Logs.DATE)),
                    from == null ? null : DateRules.parse(from),
                    optional(data, Schema.Logs.REASON),
                    text(data, Schema.Logs.ACTOR_ID),
                    text(data, Schema.Logs.ACTOR_NAME));
        } catch (RuntimeException e) {
            return null;
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
        return value instanceof String ? (String) value : null;
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
