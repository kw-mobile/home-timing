package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Input rules for registering, editing and completing a replacement item (docs/MVP.md, F-02·F-05).
 *
 * <p>Name, start date and cycle are the only required inputs. Past start dates are allowed and
 * simply produce an overdue item; dates after today (Asia/Seoul) are rejected. Length limits match
 * firestore.rules v1 so the form reports them before the server would refuse the write.
 */
public final class ItemInputValidator {
    public static final int NAME_MAX_LENGTH = 60;
    public static final int CATEGORY_MAX_LENGTH = 30;
    public static final int PRODUCT_NAME_MAX_LENGTH = 60;
    public static final int LOCATION_MAX_LENGTH = 30;
    public static final int MEMO_MAX_LENGTH = 500;

    public enum Error {
        NAME_REQUIRED,
        NAME_TOO_LONG,
        CATEGORY_INVALID,
        START_DATE_REQUIRED,
        START_DATE_IN_FUTURE,
        /** Edit: a completed item cannot start after its last actual replacement. */
        START_DATE_AFTER_LAST_REPLACED,
        CYCLE_REQUIRED,
        CYCLE_OUT_OF_RANGE,
        PRODUCT_NAME_TOO_LONG,
        LOCATION_TOO_LONG,
        MEMO_TOO_LONG,
        REPLACED_DATE_REQUIRED,
        REPLACED_DATE_IN_FUTURE,
        /** #20: the actual replacement date cannot be before the previous one. */
        REPLACED_DATE_BEFORE_PREVIOUS
    }

    private ItemInputValidator() {
    }

    /** Every problem with the required item fields; empty when the input can be saved. */
    public static Set<Error> validateItem(
            String name, LocalDate startDate, Integer cycleDays, LocalDate today) {
        Objects.requireNonNull(today, "today");
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            errors.add(Error.NAME_REQUIRED);
        } else if (length(trimmed) > NAME_MAX_LENGTH) {
            errors.add(Error.NAME_TOO_LONG);
        }
        if (startDate == null) {
            errors.add(Error.START_DATE_REQUIRED);
        } else if (startDate.isAfter(today)) {
            errors.add(Error.START_DATE_IN_FUTURE);
        }
        if (cycleDays == null) {
            errors.add(Error.CYCLE_REQUIRED);
        } else if (!ReplacementCalculator.isValidCycle(cycleDays)) {
            errors.add(Error.CYCLE_OUT_OF_RANGE);
        }
        return Collections.unmodifiableSet(errors);
    }

    /** Every problem with a whole draft: required fields, category and optional text limits. */
    public static Set<Error> validateDraft(ItemDraft draft, LocalDate today) {
        Objects.requireNonNull(draft, "draft");
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        errors.addAll(validateItem(draft.name, draft.startDate, draft.cycleDays, today));
        if (draft.category.isEmpty() || length(draft.category) > CATEGORY_MAX_LENGTH) {
            errors.add(Error.CATEGORY_INVALID);
        }
        if (length(draft.productName) > PRODUCT_NAME_MAX_LENGTH) {
            errors.add(Error.PRODUCT_NAME_TOO_LONG);
        }
        if (length(draft.location) > LOCATION_MAX_LENGTH) {
            errors.add(Error.LOCATION_TOO_LONG);
        }
        if (length(draft.memo) > MEMO_MAX_LENGTH) {
            errors.add(Error.MEMO_TOO_LONG);
        }
        return Collections.unmodifiableSet(errors);
    }

    /**
     * Problems with the actual replacement date chosen when completing an item:
     * {@code previousReplacedDate ≤ replacedDate ≤ today} (Asia/Seoul, #20). The same day is allowed.
     */
    public static Set<Error> validateReplacedDate(
            LocalDate replacedDate, LocalDate previousReplacedDate, LocalDate today) {
        Objects.requireNonNull(previousReplacedDate, "previousReplacedDate");
        Objects.requireNonNull(today, "today");
        if (replacedDate == null) {
            return Collections.unmodifiableSet(EnumSet.of(Error.REPLACED_DATE_REQUIRED));
        }
        if (replacedDate.isAfter(today)) {
            return Collections.unmodifiableSet(EnumSet.of(Error.REPLACED_DATE_IN_FUTURE));
        }
        if (replacedDate.isBefore(previousReplacedDate)) {
            return Collections.unmodifiableSet(EnumSet.of(Error.REPLACED_DATE_BEFORE_PREVIOUS));
        }
        return Collections.emptySet();
    }

    /** Characters, not UTF-16 units, so emoji count once like the security rules. */
    static int length(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }
}
