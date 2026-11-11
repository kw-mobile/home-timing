package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Input rules for weekly chores (docs/MVP.md F-07). Limits match firestore.rules so the form reports
 * them before the server would refuse the write.
 */
public final class ChoreInputValidator {
    public static final int NAME_MAX_LENGTH = 60;
    public static final int LOCATION_MAX_LENGTH = 30;
    public static final int MEMO_MAX_LENGTH = 500;

    private static final Set<String> TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            ChoreDraft.TYPE_BEDDING_WASH, ChoreDraft.TYPE_OUTDOOR_DRY, ChoreDraft.TYPE_VENTILATION,
            ChoreDraft.TYPE_CLEANING, ChoreDraft.TYPE_ETC)));

    public enum Error {
        NAME_REQUIRED,
        NAME_TOO_LONG,
        TYPE_INVALID,
        WEEKDAY_REQUIRED,
        WEEKDAY_INVALID,
        START_DATE_REQUIRED,
        START_DATE_IN_FUTURE,
        LOCATION_TOO_LONG,
        MEMO_TOO_LONG,
        DONE_DATE_REQUIRED,
        DONE_DATE_IN_FUTURE
    }

    private ChoreInputValidator() {
    }

    public static Set<Error> validateDraft(ChoreDraft draft, LocalDate today) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(today, "today");
        EnumSet<Error> errors = EnumSet.noneOf(Error.class);
        if (draft.name.isEmpty()) {
            errors.add(Error.NAME_REQUIRED);
        } else if (ItemInputValidator.length(draft.name) > NAME_MAX_LENGTH) {
            errors.add(Error.NAME_TOO_LONG);
        }
        if (!TYPES.contains(draft.type)) {
            errors.add(Error.TYPE_INVALID);
        }
        if (draft.weekday == null) {
            errors.add(Error.WEEKDAY_REQUIRED);
        } else if (draft.weekday < 1 || draft.weekday > 7) {
            errors.add(Error.WEEKDAY_INVALID);
        }
        if (draft.startDate == null) {
            errors.add(Error.START_DATE_REQUIRED);
        } else if (draft.startDate.isAfter(today)) {
            errors.add(Error.START_DATE_IN_FUTURE);
        }
        if (ItemInputValidator.length(draft.location) > LOCATION_MAX_LENGTH) {
            errors.add(Error.LOCATION_TOO_LONG);
        }
        if (ItemInputValidator.length(draft.memo) > MEMO_MAX_LENGTH) {
            errors.add(Error.MEMO_TOO_LONG);
        }
        return Collections.unmodifiableSet(errors);
    }

    /** The completion date may not be in the future (docs/MVP.md). */
    public static Set<Error> validateDoneDate(LocalDate doneDate, LocalDate today) {
        Objects.requireNonNull(today, "today");
        if (doneDate == null) {
            return Collections.unmodifiableSet(EnumSet.of(Error.DONE_DATE_REQUIRED));
        }
        if (doneDate.isAfter(today)) {
            return Collections.unmodifiableSet(EnumSet.of(Error.DONE_DATE_IN_FUTURE));
        }
        return Collections.emptySet();
    }
}
