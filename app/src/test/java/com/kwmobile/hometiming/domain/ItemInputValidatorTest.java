package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.ItemInputValidator.Error;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

public class ItemInputValidatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    @Test
    public void acceptsRequiredFieldsOnly() {
        assertTrue(ItemInputValidator.validateItem("샤워기 필터", TODAY, 60, TODAY).isEmpty());
    }

    @Test
    public void acceptsPastStartDate() {
        assertTrue(ItemInputValidator.validateItem("칫솔", TODAY.minusYears(1), 90, TODAY).isEmpty());
    }

    @Test
    public void rejectsBlankName() {
        assertErrors(EnumSet.of(Error.NAME_REQUIRED),
                ItemInputValidator.validateItem("   ", TODAY, 60, TODAY));
        assertErrors(EnumSet.of(Error.NAME_REQUIRED),
                ItemInputValidator.validateItem(null, TODAY, 60, TODAY));
    }

    @Test
    public void rejectsStartDateAfterToday() {
        assertErrors(EnumSet.of(Error.START_DATE_IN_FUTURE),
                ItemInputValidator.validateItem("필터", TODAY.plusDays(1), 60, TODAY));
        assertErrors(EnumSet.of(Error.START_DATE_REQUIRED),
                ItemInputValidator.validateItem("필터", null, 60, TODAY));
    }

    @Test
    public void checksCycleRange() {
        assertTrue(ItemInputValidator.validateItem("필터", TODAY, 1, TODAY).isEmpty());
        assertTrue(ItemInputValidator.validateItem("필터", TODAY, 730, TODAY).isEmpty());
        assertErrors(EnumSet.of(Error.CYCLE_OUT_OF_RANGE),
                ItemInputValidator.validateItem("필터", TODAY, 0, TODAY));
        assertErrors(EnumSet.of(Error.CYCLE_OUT_OF_RANGE),
                ItemInputValidator.validateItem("필터", TODAY, 731, TODAY));
        assertErrors(EnumSet.of(Error.CYCLE_REQUIRED),
                ItemInputValidator.validateItem("필터", TODAY, null, TODAY));
    }

    @Test
    public void reportsEveryProblemAtOnce() {
        assertErrors(EnumSet.of(Error.NAME_REQUIRED, Error.START_DATE_IN_FUTURE,
                        Error.CYCLE_OUT_OF_RANGE),
                ItemInputValidator.validateItem("", TODAY.plusDays(3), 1000, TODAY));
    }

    @Test
    public void replacedDateMayBeTodayOrPastButNotFuture() {
        LocalDate previous = TODAY.minusDays(30);

        assertTrue(ItemInputValidator.validateReplacedDate(TODAY, previous, TODAY).isEmpty());
        assertTrue(ItemInputValidator.validateReplacedDate(TODAY.minusDays(10), previous, TODAY).isEmpty());
        assertErrors(EnumSet.of(Error.REPLACED_DATE_IN_FUTURE),
                ItemInputValidator.validateReplacedDate(TODAY.plusDays(1), previous, TODAY));
        assertErrors(EnumSet.of(Error.REPLACED_DATE_REQUIRED),
                ItemInputValidator.validateReplacedDate(null, previous, TODAY));
    }

    @Test
    public void replacedDateCannotGoBeforePreviousReplacement() {
        LocalDate previous = TODAY.minusDays(30);

        // #20: same day as the previous replacement is fine, one day earlier is not.
        assertTrue(ItemInputValidator.validateReplacedDate(previous, previous, TODAY).isEmpty());
        assertErrors(EnumSet.of(Error.REPLACED_DATE_BEFORE_PREVIOUS),
                ItemInputValidator.validateReplacedDate(previous.minusDays(1), previous, TODAY));
    }

    @Test
    public void nameIsAtMostSixtyCharacters() {
        assertTrue(ItemInputValidator.validateItem(repeat("가", 60), TODAY, 60, TODAY).isEmpty());
        assertErrors(EnumSet.of(Error.NAME_TOO_LONG),
                ItemInputValidator.validateItem(repeat("가", 61), TODAY, 60, TODAY));
    }

    @Test
    public void draftChecksCategoryAndOptionalText() {
        ItemDraft ok = new ItemDraft("필터", null, TODAY, 60, repeat("p", 60), repeat("l", 30), repeat("m", 500));
        assertTrue(ItemInputValidator.validateDraft(ok, TODAY).isEmpty());
        assertEquals(ItemDraft.DEFAULT_CATEGORY, ok.category);

        ItemDraft tooLong = new ItemDraft("필터", repeat("c", 31), TODAY, 60,
                repeat("p", 61), repeat("l", 31), repeat("m", 501));
        assertErrors(EnumSet.of(Error.CATEGORY_INVALID, Error.PRODUCT_NAME_TOO_LONG,
                Error.LOCATION_TOO_LONG, Error.MEMO_TOO_LONG), ItemInputValidator.validateDraft(tooLong, TODAY));
    }

    private static String repeat(String s, int times) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < times; i++) {
            out.append(s);
        }
        return out.toString();
    }

    private static void assertErrors(Set<Error> expected, Set<Error> actual) {
        assertEquals(expected, actual);
    }
}
