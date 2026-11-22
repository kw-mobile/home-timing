package com.kwmobile.hometiming.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Next states of a weekly chore (docs/MVP.md 완료·반복 계약). Pure Java: the repository writes the
 * result in a transaction. Moving a date keeps the occurrence and its original date; only
 * completing starts a new occurrence.
 */
public final class ChoreSchedule {
    private static final int DAYS_PER_WEEK = 7;

    private ChoreSchedule() {
    }

    /** The first occurrence: the next such weekday after the start date. */
    public static LocalDate firstDue(LocalDate startDate, int weekday) {
        Objects.requireNonNull(startDate, "startDate");
        return startDate.with(TemporalAdjusters.next(DayOfWeek.of(weekday)));
    }

    /** A new chore: the first occurrence after the start date, revision 1. The draft must be valid. */
    public static Chore register(String id, ChoreDraft draft, String uid, String occurrenceId) {
        Objects.requireNonNull(draft, "draft");
        LocalDate first = firstDue(draft.startDate, draft.weekday);
        return new Chore(id, draft.name, draft.type, draft.weatherSensitive, draft.weekday, first, first,
                occurrenceId, 1, null, null, draft.location, draft.memo, uid, draft.categoryId, draft.category);
    }

    /**
     * An edit keeps the occurrence. Changing the weekday restarts this occurrence on the next such
     * weekday after today; otherwise the dates stay. Revision goes up by one only when the weekday
     * or weather sensitivity changes.
     */
    public static Chore edit(Chore current, ChoreDraft draft, LocalDate today) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(draft, "draft");
        boolean weekdayChanged = current.weekday != draft.weekday;
        LocalDate planned = weekdayChanged ? firstDue(today, draft.weekday) : current.plannedDueDate;
        LocalDate next = weekdayChanged ? planned : current.nextDueDate;
        boolean scheduleChanged = weekdayChanged || current.weatherSensitive != draft.weatherSensitive;
        return new Chore(current.id, draft.name, draft.type, draft.weatherSensitive, draft.weekday, planned, next,
                current.occurrenceId, current.revision + (scheduleChanged ? 1 : 0),
                current.dismissedOccurrenceId, current.lastDoneDate, draft.location, draft.memo, current.createdBy, draft.categoryId, draft.category);
    }

    /**
     * The next occurrence: from the week after the original date, the first original weekday after
     * the completion date. Missed weeks are skipped, not created.
     */
    public static LocalDate nextPlanned(LocalDate plannedDueDate, LocalDate doneDate) {
        Objects.requireNonNull(plannedDueDate, "plannedDueDate");
        Objects.requireNonNull(doneDate, "doneDate");
        LocalDate next = plannedDueDate.plusDays(DAYS_PER_WEEK);
        while (!next.isAfter(doneDate)) {
            next = next.plusDays(DAYS_PER_WEEK);
        }
        return next;
    }

    /**
     * Completion starts a new occurrence on the original weekday and clears the dismissal, so the
     * new occurrence's suggestion is evaluated again.
     *
     * @throws IllegalArgumentException if the occurrence id is not new
     */
    public static Chore complete(Chore current, LocalDate doneDate, String newOccurrenceId) {
        Objects.requireNonNull(current, "current");
        if (current.occurrenceId.equals(newOccurrenceId)) {
            throw new IllegalArgumentException("completion needs a new occurrence");
        }
        LocalDate next = nextPlanned(current.plannedDueDate, doneDate);
        return new Chore(current.id, current.name, current.type, current.weatherSensitive, current.weekday,
                next, next, newOccurrenceId, current.revision + 1, null, doneDate, current.location,
                current.memo, current.createdBy, current.categoryId, current.category);
    }

    /** Whether this occurrence may move to that date: today or later. */
    public static boolean canReschedule(LocalDate target, LocalDate today) {
        return target != null && !target.isBefore(today);
    }

    /**
     * Moves only this occurrence. The occurrence and its original date stay, so the following
     * occurrence still falls on the original weekday. Moving to the same date changes nothing.
     */
    public static Chore reschedule(Chore current, LocalDate target) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(target, "target");
        if (target.equals(current.nextDueDate)) {
            return current;
        }
        return new Chore(current.id, current.name, current.type, current.weatherSensitive, current.weekday,
                current.plannedDueDate, target, current.occurrenceId, current.revision + 1,
                current.dismissedOccurrenceId, current.lastDoneDate, current.location, current.memo,
                current.createdBy, current.categoryId, current.category);
    }

    /** "그대로 두기": hides the suggestion for this occurrence only. */
    public static Chore dismissSuggestion(Chore current) {
        Objects.requireNonNull(current, "current");
        if (current.isSuggestionDismissed()) {
            return current;
        }
        return new Chore(current.id, current.name, current.type, current.weatherSensitive, current.weekday,
                current.plannedDueDate, current.nextDueDate, current.occurrenceId, current.revision + 1,
                current.occurrenceId, current.lastDoneDate, current.location, current.memo, current.createdBy, current.categoryId, current.category);
    }

    /** Compares the stored chore with what the screen or suggestion was based on. */
    public static ItemSchedule.Conflict conflict(Chore stored, String expectedOccurrenceId, int expectedRevision) {
        if (!stored.occurrenceId.equals(expectedOccurrenceId)) {
            return ItemSchedule.Conflict.ALREADY_COMPLETED;
        }
        if (stored.revision != expectedRevision) {
            return ItemSchedule.Conflict.SCHEDULE_CHANGED;
        }
        return ItemSchedule.Conflict.NONE;
    }
}
