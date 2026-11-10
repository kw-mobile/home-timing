package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Next states of an item for register, edit and complete (docs/MVP.md 완료·반복 계약,
 * docs/DATA_MODEL.md). Pure Java: the repository writes the result inside a transaction, and
 * firestore.rules checks the same invariants again.
 */
public final class ItemSchedule {
    /** Why a write based on what the screen showed can no longer apply. */
    public enum Conflict {
        NONE,
        /** The occurrence the screen showed was already completed (by this or another device). */
        ALREADY_COMPLETED,
        /** Same occurrence, but its schedule changed since the screen loaded it. */
        SCHEDULE_CHANGED
    }

    private ItemSchedule() {
    }

    /** A new item: last replacement = start date, revision 1. The draft must be valid. */
    public static Item register(String id, ItemDraft draft, String uid, String occurrenceId) {
        requireComplete(draft);
        return new Item(id, draft.name, draft.category, draft.startDate, draft.cycleDays,
                draft.startDate, ReplacementCalculator.nextDueDate(draft.startDate, draft.cycleDays),
                occurrenceId, 1, draft.productName, draft.location, draft.memo, uid);
    }

    /**
     * An edit (F-03) keeps the occurrence. If the item was never completed after its start date,
     * moving the start date moves the last replacement with it; otherwise the last replacement stays
     * and the start date may not pass it. Revision goes up by one only when the schedule changes.
     *
     * @throws IllegalArgumentException when the start date would pass the last replacement; check
     *     {@link #startDateFits} first to show {@code START_DATE_AFTER_LAST_REPLACED}
     */
    public static Item edit(Item current, ItemDraft draft) {
        Objects.requireNonNull(current, "current");
        requireComplete(draft);
        LocalDate last = neverReplacedSinceStart(current) ? draft.startDate : current.lastReplacedDate;
        if (draft.startDate.isAfter(last)) {
            throw new IllegalArgumentException("startDate after lastReplacedDate");
        }
        Item next = new Item(current.id, draft.name, draft.category, draft.startDate, draft.cycleDays,
                last, ReplacementCalculator.nextDueDate(last, draft.cycleDays), current.occurrenceId,
                current.revision, draft.productName, draft.location, draft.memo, current.createdBy);
        return next.sameSchedule(current) ? next : withRevision(next, current.revision + 1);
    }

    /** Whether {@link #edit} accepts the draft's start date for this item. */
    public static boolean startDateFits(Item current, LocalDate startDate) {
        return neverReplacedSinceStart(current) || !startDate.isAfter(current.lastReplacedDate);
    }

    /**
     * Completion (F-05): the next cycle counts from the actual replacement date, a new occurrence
     * starts, revision +1. The date must pass {@link ItemInputValidator#validateReplacedDate}.
     */
    public static Item complete(Item current, LocalDate replacedDate, String newOccurrenceId) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(replacedDate, "replacedDate");
        if (replacedDate.isBefore(current.lastReplacedDate)) {
            throw new IllegalArgumentException("replacedDate before lastReplacedDate");
        }
        if (current.occurrenceId.equals(newOccurrenceId)) {
            throw new IllegalArgumentException("completion needs a new occurrence");
        }
        return new Item(current.id, current.name, current.category, current.startDate, current.cycleDays,
                replacedDate, ReplacementCalculator.nextDueDate(replacedDate, current.cycleDays),
                newOccurrenceId, current.revision + 1, current.productName, current.location,
                current.memo, current.createdBy);
    }

    /** The history entry written with {@link #complete}, keyed by the finished occurrence. */
    public static HistoryEntry historyOf(Item before, LocalDate replacedDate, String uid, String name) {
        return new HistoryEntry(before.occurrenceId, replacedDate, before.nextDueDate, before.cycleDays,
                uid, name);
    }

    /**
     * Compares the stored item with what the screen showed. Completion and edit both send the
     * occurrence and revision they were based on.
     */
    public static Conflict conflict(Item stored, String expectedOccurrenceId, int expectedRevision) {
        if (!stored.occurrenceId.equals(expectedOccurrenceId)) {
            return Conflict.ALREADY_COMPLETED;
        }
        if (stored.revision != expectedRevision) {
            return Conflict.SCHEDULE_CHANGED;
        }
        return Conflict.NONE;
    }

    private static boolean neverReplacedSinceStart(Item item) {
        return item.lastReplacedDate.equals(item.startDate);
    }

    private static Item withRevision(Item item, int revision) {
        return new Item(item.id, item.name, item.category, item.startDate, item.cycleDays,
                item.lastReplacedDate, item.nextDueDate, item.occurrenceId, revision, item.productName,
                item.location, item.memo, item.createdBy);
    }

    private static void requireComplete(ItemDraft draft) {
        Objects.requireNonNull(draft, "draft");
        if (draft.startDate == null || draft.cycleDays == null || draft.name.isEmpty()) {
            throw new IllegalArgumentException("draft is incomplete; validate it first");
        }
    }
}
