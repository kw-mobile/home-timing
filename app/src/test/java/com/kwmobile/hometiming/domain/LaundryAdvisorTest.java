package com.kwmobile.hometiming.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.LaundryAdvisor.Verdict;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class LaundryAdvisorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static DailyForecast day(LocalDate date, int maxPop, boolean precip, double reh) {
        return new DailyForecast(date, true, maxPop, precip, reh);
    }

    private static DailyForecast good(LocalDate date) {
        return day(date, 10, false, 50);
    }

    private static DailyForecast bad(LocalDate date) {
        return day(date, 80, false, 50);
    }

    /** Complete 09–18 h for a date with the same values each hour. */
    private static List<ForecastHour> hours(LocalDate date, int from, int to, Integer pop, Integer pty, Integer reh) {
        List<ForecastHour> list = new ArrayList<>();
        for (int hour = from; hour <= to; hour++) {
            list.add(new ForecastHour(date, hour, pop, pty, reh));
        }
        return list;
    }

    @Test
    public void judgesPopBoundaries() {
        assertEquals(Verdict.GOOD, LaundryAdvisor.judge(day(TODAY, 29, false, 50)));
        assertEquals(Verdict.CAUTION, LaundryAdvisor.judge(day(TODAY, 30, false, 50)));
        assertEquals(Verdict.CAUTION, LaundryAdvisor.judge(day(TODAY, 59, false, 50)));
        assertEquals(Verdict.BAD, LaundryAdvisor.judge(day(TODAY, 60, false, 50)));
    }

    @Test
    public void judgesHumidityBoundary() {
        assertEquals(Verdict.GOOD, LaundryAdvisor.judge(day(TODAY, 0, false, 79.9)));
        assertEquals(Verdict.CAUTION, LaundryAdvisor.judge(day(TODAY, 0, false, 80)));
    }

    @Test
    public void anyPrecipitationIsBadEvenAtLowProbability() {
        assertEquals(Verdict.BAD, LaundryAdvisor.judge(day(TODAY, 0, true, 40)));
    }

    @Test
    public void missingForecastIsNeverSuitable() {
        assertEquals(Verdict.UNKNOWN, LaundryAdvisor.judge(null));
        assertEquals(Verdict.UNKNOWN, LaundryAdvisor.judge(new DailyForecast(TODAY, false, 0, false, 0)));
    }

    @Test
    public void summarizesOnlyTheDryingWindow() {
        LocalDate date = TODAY.plusDays(1);
        List<ForecastHour> list = hours(date, 9, 18, 10, 0, 50);
        // Rain outside 09–18 does not count.
        list.add(new ForecastHour(date, 8, 90, 1, 95));
        list.add(new ForecastHour(date, 19, 90, 1, 95));

        DailyForecast forecast = DailyForecast.summarize(list, TODAY, 6).get(date);

        assertTrue(forecast.complete);
        assertEquals(10, forecast.maxPop);
        assertFalse(forecast.hasPrecip);
        assertEquals(50.0, forecast.avgReh, 0.001);
    }

    @Test
    public void everyPrecipitationTypeCounts() {
        for (int pty = 1; pty <= 4; pty++) {
            LocalDate date = TODAY.plusDays(1);
            List<ForecastHour> list = hours(date, 9, 17, 0, 0, 50);
            list.add(new ForecastHour(date, 18, 0, pty, 50));

            assertEquals("PTY " + pty, Verdict.BAD, LaundryAdvisor.judge(DailyForecast.summarize(list, TODAY, 6).get(date)));
        }
    }

    @Test
    public void aMissingHourOrValueMakesTheDayUnknown() {
        LocalDate date = TODAY.plusDays(1);
        List<ForecastHour> missingHour = hours(date, 9, 17, 0, 0, 50);
        assertEquals(Verdict.UNKNOWN, LaundryAdvisor.judge(DailyForecast.summarize(missingHour, TODAY, 6).get(date)));

        List<ForecastHour> missingValue = hours(date, 9, 17, 0, 0, 50);
        missingValue.add(new ForecastHour(date, 18, 0, 0, null));
        assertEquals(Verdict.UNKNOWN, LaundryAdvisor.judge(DailyForecast.summarize(missingValue, TODAY, 6).get(date)));
    }

    @Test
    public void todayNeedsOnlyTheHoursStillAhead() {
        // At 14:xx the run has no 09–14 hours for today; 15–18 are enough.
        Map<LocalDate, DailyForecast> days = DailyForecast.summarize(hours(TODAY, 15, 18, 10, 0, 50), TODAY, 14);

        assertTrue(days.get(TODAY).complete);
        assertEquals(Verdict.GOOD, LaundryAdvisor.judge(days.get(TODAY)));
    }

    @Test
    public void todayIsLeftOutOnceTheWindowIsOver() {
        assertFalse(DailyForecast.summarize(hours(TODAY, 9, 18, 0, 0, 50), TODAY, 18).containsKey(TODAY));
        assertTrue(DailyForecast.summarize(hours(TODAY, 18, 18, 0, 0, 50), TODAY, 17).containsKey(TODAY));
    }

    @Test
    public void pastDaysAreDropped() {
        assertTrue(DailyForecast.summarize(hours(TODAY.minusDays(1), 9, 18, 0, 0, 50), TODAY, 6).isEmpty());
    }

    private static Map<LocalDate, DailyForecast> forecast(DailyForecast... days) {
        Map<LocalDate, DailyForecast> map = new HashMap<>();
        for (DailyForecast day : days) {
            map.put(day.date, day);
        }
        return map;
    }

    @Test
    public void prefersTheNearestLaterDay() {
        LocalDate due = TODAY.plusDays(1);

        assertEquals(TODAY.plusDays(2), LaundryAdvisor.findAlternative(due,
                forecast(good(TODAY), bad(due), good(TODAY.plusDays(2))), TODAY));
        // An absent day is unknown, not the end of the available forecast range.
        assertEquals(TODAY.plusDays(3), LaundryAdvisor.findAlternative(due,
                forecast(good(TODAY), bad(due), good(TODAY.plusDays(3))), TODAY));
    }

    @Test
    public void fallsBackToAnEarlierDayNotBeforeToday() {
        LocalDate due = TODAY.plusDays(2);
        Map<LocalDate, DailyForecast> days = forecast(good(TODAY), bad(TODAY.plusDays(1)), bad(due), bad(TODAY.plusDays(3)));

        assertEquals(TODAY, LaundryAdvisor.findAlternative(due, days, TODAY));
        assertNull(LaundryAdvisor.findAlternative(due, days, TODAY.plusDays(1)));
    }

    @Test
    public void noSuitableDayMeansNoAlternative() {
        LocalDate due = TODAY.plusDays(1);

        assertNull(LaundryAdvisor.findAlternative(due, forecast(bad(TODAY), bad(due), bad(TODAY.plusDays(2))), TODAY));
    }

    @Test
    public void cautionAndUnknownDaysAreNotAlternatives() {
        LocalDate due = TODAY;
        Map<LocalDate, DailyForecast> days = forecast(bad(due), day(TODAY.plusDays(1), 40, false, 50),
                new DailyForecast(TODAY.plusDays(2), false, 0, false, 0));

        assertNull(LaundryAdvisor.findAlternative(due, days, TODAY));
    }
}
