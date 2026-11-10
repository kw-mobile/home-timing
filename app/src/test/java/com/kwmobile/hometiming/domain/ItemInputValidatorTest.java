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
        assertTrue(ItemInputValidator.validateReplacedDate(TODAY, TODAY).isEmpty());
        assertTrue(ItemInputValidator.validateReplacedDate(TODAY.minusDays(10), TODAY).isEmpty());
        assertErrors(EnumSet.of(Error.REPLACED_DATE_IN_FUTURE),
                ItemInputValidator.validateReplacedDate(TODAY.plusDays(1), TODAY));
        assertErrors(EnumSet.of(Error.REPLACED_DATE_REQUIRED),
                ItemInputValidator.validateReplacedDate(null, TODAY));
    }

    private static void assertErrors(Set<Error> expected, Set<Error> actual) {
        assertEquals(expected, actual);
    }
}
