package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ChoreInputValidator.Error;

import java.time.LocalDate;
import java.util.EnumSet;

import org.junit.Test;

public class ChoreInputValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static String repeat(String s, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(s);
        }
        return out.toString();
    }

    private static ChoreDraft draft(String name, String type, Integer weekday, LocalDate start) {
        return new ChoreDraft(name, type, true, weekday, start, "", "");
    }

    @Test
    public void acceptsAValidDraftAndDefaultsTheType() {
        ChoreDraft draft = draft("침구 세탁", null, 6, TODAY);

        assertTrue(ChoreInputValidator.validateDraft(draft, TODAY).isEmpty());
        assertEquals(ChoreDraft.TYPE_ETC, draft.type);
    }

    @Test
    public void nameIsRequiredAndBounded() {
        assertEquals(EnumSet.of(Error.NAME_REQUIRED), ChoreInputValidator.validateDraft(draft("  ", "ETC", 6, TODAY), TODAY));
        assertTrue(ChoreInputValidator.validateDraft(draft(repeat("가", 60), "ETC", 6, TODAY), TODAY).isEmpty());
        assertEquals(EnumSet.of(Error.NAME_TOO_LONG),
                ChoreInputValidator.validateDraft(draft(repeat("가", 61), "ETC", 6, TODAY), TODAY));
    }

    @Test
    public void typeMustBeKnown() {
        assertEquals(EnumSet.of(Error.TYPE_INVALID), ChoreInputValidator.validateDraft(draft("a", "GARDEN", 6, TODAY), TODAY));
    }

    @Test
    public void weekdayIsOneToSeven() {
        assertEquals(EnumSet.of(Error.WEEKDAY_REQUIRED), ChoreInputValidator.validateDraft(draft("a", "ETC", null, TODAY), TODAY));
        assertEquals(EnumSet.of(Error.WEEKDAY_INVALID), ChoreInputValidator.validateDraft(draft("a", "ETC", 0, TODAY), TODAY));
        assertEquals(EnumSet.of(Error.WEEKDAY_INVALID), ChoreInputValidator.validateDraft(draft("a", "ETC", 8, TODAY), TODAY));
        assertTrue(ChoreInputValidator.validateDraft(draft("a", "ETC", 1, TODAY), TODAY).isEmpty());
        assertTrue(ChoreInputValidator.validateDraft(draft("a", "ETC", 7, TODAY), TODAY).isEmpty());
    }

    @Test
    public void startDateCannotBeInTheFuture() {
        assertEquals(EnumSet.of(Error.START_DATE_REQUIRED), ChoreInputValidator.validateDraft(draft("a", "ETC", 6, null), TODAY));
        assertEquals(EnumSet.of(Error.START_DATE_IN_FUTURE),
                ChoreInputValidator.validateDraft(draft("a", "ETC", 6, TODAY.plusDays(1)), TODAY));
    }

    @Test
    public void optionalTextIsBounded() {
        ChoreDraft draft = new ChoreDraft("a", "ETC", false, 6, TODAY, repeat("l", 31), repeat("m", 501));

        assertEquals(EnumSet.of(Error.LOCATION_TOO_LONG, Error.MEMO_TOO_LONG), ChoreInputValidator.validateDraft(draft, TODAY));
    }

    @Test
    public void doneDateMayBeTodayOrPast() {
        assertTrue(ChoreInputValidator.validateDoneDate(TODAY, TODAY).isEmpty());
        assertTrue(ChoreInputValidator.validateDoneDate(TODAY.minusDays(30), TODAY).isEmpty());
        assertEquals(EnumSet.of(Error.DONE_DATE_IN_FUTURE), ChoreInputValidator.validateDoneDate(TODAY.plusDays(1), TODAY));
        assertEquals(EnumSet.of(Error.DONE_DATE_REQUIRED), ChoreInputValidator.validateDoneDate(null, TODAY));
    }

    @Test
    public void laundryLikeTypesDefaultToWeatherSensitive() {
        assertTrue(ChoreDraft.defaultWeatherSensitive(ChoreDraft.TYPE_BEDDING_WASH));
        assertTrue(ChoreDraft.defaultWeatherSensitive(ChoreDraft.TYPE_OUTDOOR_DRY));
        assertFalse(ChoreDraft.defaultWeatherSensitive(ChoreDraft.TYPE_CLEANING));
    }
}
