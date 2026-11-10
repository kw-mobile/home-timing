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
 * simply produce an overdue item; dates after today (Asia/Seoul) are rejected.
 */
public final class ItemInputValidator {
    public enum Error {
        NAME_REQUIRED,
        START_DATE_REQUIRED,
        START_DATE_IN_FUTURE,
        CYCLE_REQUIRED,
        CYCLE_OUT_OF_RANGE,
        REPLACED_DATE_REQUIRED,
        REPLACED_DATE_IN_FUTURE
    }

    private ItemInputValidator() {
    }

    /** Every problem with the required item fields; empty when the input can be saved. */
    public static Set<Error> validateItem(
            String name, LocalDate startDate, Integer cycleDays, LocalDate today) {
        Objects.requireNonNull(today, "today");
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        if (name == null || name.trim().isEmpty()) {
            errors.add(Error.NAME_REQUIRED);
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

    /** Problems with the actual replacement date chosen when completing an item. */
    public static Set<Error> validateReplacedDate(LocalDate replacedDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (replacedDate == null) {
            return Collections.unmodifiableSet(EnumSet.of(Error.REPLACED_DATE_REQUIRED));
        }
        if (replacedDate.isAfter(today)) {
            return Collections.unmodifiableSet(EnumSet.of(Error.REPLACED_DATE_IN_FUTURE));
        }
        return Collections.emptySet();
    }
}
