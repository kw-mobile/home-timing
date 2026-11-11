package com.kwmobile.hometiming.domain;

import java.util.Objects;

/**
 * Identifies one local reminder (docs/MVP.md 날씨·알림 계약): item kind, house, document, reminder
 * type and occurrence. A new occurrence gets a new key, so a finished one can be cancelled without
 * touching the next. Use {@link #value()} as the notification tag and alarm identity.
 */
public final class ReminderKey {
    public enum Kind {
        ITEM,
        CHORE
    }

    public enum Type {
        /** Days before the due date, from the personal setting. */
        ADVANCE,
        /** On the due date. */
        DUE
    }

    // Firestore ids cannot contain '/'. The occurrence goes last so it is never split.
    private static final String SEPARATOR = "/";
    private static final int PARTS = 5;

    public final Kind kind;
    public final String houseId;
    public final String documentId;
    public final Type type;
    public final String occurrenceId;

    public ReminderKey(Kind kind, String houseId, String documentId, Type type, String occurrenceId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.houseId = requireId(houseId, "houseId");
        this.documentId = requireId(documentId, "documentId");
        this.type = Objects.requireNonNull(type, "type");
        this.occurrenceId = Objects.requireNonNull(occurrenceId, "occurrenceId");
        if (occurrenceId.isEmpty()) {
            throw new IllegalArgumentException("occurrenceId");
        }
    }

    private static String requireId(String id, String name) {
        if (id == null || id.isEmpty() || id.contains(SEPARATOR)) {
            throw new IllegalArgumentException(name);
        }
        return id;
    }

    public String value() {
        return kind + SEPARATOR + houseId + SEPARATOR + documentId + SEPARATOR + type + SEPARATOR + occurrenceId;
    }

    /** Null when the text is not a key, so a stale alarm from an older format can be ignored. */
    public static ReminderKey parse(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split(SEPARATOR, PARTS);
        if (parts.length != PARTS) {
            return null;
        }
        try {
            return new ReminderKey(Kind.valueOf(parts[0]), parts[1], parts[2], Type.valueOf(parts[3]), parts[4]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReminderKey && value().equals(((ReminderKey) other).value());
    }

    @Override
    public int hashCode() {
        return value().hashCode();
    }

    @Override
    public String toString() {
        return value();
    }
}
