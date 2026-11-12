package com.kwmobile.hometiming.ui.main;

import com.kwmobile.hometiming.domain.ForecastHour;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherDisplayTest {
    private final LocalDate day = LocalDate.of(2026, 11, 12);
    private ForecastHour hour(LocalDate date, int hour, Double temperature, Integer sky, Integer pty) {
        return new ForecastHour(date, hour, 10, pty, 50, temperature, sky, null, null);
    }

    @Test public void selectsThisHourAndNeverUsesAnEarlierTemperature() {
        ForecastHour current = hour(day, 12, 13.0, 1, 0);
        WeatherDisplay display = new WeatherDisplay(Arrays.asList(
                hour(day, 14, 16.0, 1, 0), hour(day, 11, 10.0, 1, 0), current),
                day.atTime(12, 45));
        assertSame(current, display.current);
        assertNull(new WeatherDisplay(Collections.singletonList(hour(day, 11, 10.0, 1, 0)),
                day.atTime(12, 0)).current);
    }

    @Test public void showsTheNextForecastWithItsOwnTimeInsteadOfPretendingItIsAnObservation() {
        ForecastHour next = hour(day, 15, 12.5, 3, 0);
        WeatherDisplay display = new WeatherDisplay(Collections.singletonList(next), day.atTime(14, 20));
        assertSame(next, display.current);
        assertEquals(15, display.current.hour);
    }

    @Test public void tomorrowNeverBecomesTodaysTemperature() {
        WeatherDisplay display = new WeatherDisplay(Collections.singletonList(
                hour(day.plusDays(1), 0, 8.0, 1, 0)), day.atTime(23, 59));
        assertNull(display.current);
    }

    @Test public void dailyExtremesFallbackToHourlyRangeWhenOfficialExtremesAreMissing() {
        WeatherDisplay display = new WeatherDisplay(Arrays.asList(
                hour(day, 12, 18.0, 1, 0), hour(day, 15, 21.0, 1, 0)), day.atTime(11, 0));
        assertEquals(Double.valueOf(18.0), display.days.get(day).low);
        assertEquals(Double.valueOf(21.0), display.days.get(day).high);

        ForecastHour official = new ForecastHour(day, 6, 10, 0, 50, 15.0, 1, -3.0, 22.5);
        display = new WeatherDisplay(Arrays.asList(official, hour(day, 12, 18.0, 1, 0)), day.atTime(11, 0));
        assertEquals(Double.valueOf(-3), display.days.get(day).low);
        assertEquals(Double.valueOf(22.5), display.days.get(day).high);

        display = new WeatherDisplay(Collections.singletonList(
                hour(day, 12, null, 1, 0)), day.atTime(11, 0));
        assertNull(display.days.get(day).low);
        assertNull(display.days.get(day).high);

        ForecastHour h1 = new ForecastHour(day, 12, 20, 0, 50, 18.0, 1, null, null);
        ForecastHour h2 = new ForecastHour(day, 15, 60, 1, 70, 21.0, 1, null, null);
        display = new WeatherDisplay(Arrays.asList(h1, h2), day.atTime(11, 0));
        assertEquals(Integer.valueOf(60), display.days.get(day).pop);
    }

    @Test public void missingSkyOrPrecipitationDoesNotMeanSunny() {
        assertEquals("unknown", WeatherDisplay.condition(hour(day, 12, null, null, 0)));
        assertEquals("unknown", WeatherDisplay.condition(hour(day, 12, 18.0, 1, null)));
        assertEquals("unknown", WeatherDisplay.condition(null));
        assertEquals("rain", WeatherDisplay.condition(hour(day, 12, 18.0, 1, 1)));
        assertEquals("snow", WeatherDisplay.condition(hour(day, 12, -2.0, 1, 3)));
    }

    @Test public void pastDatesAreDroppedAndFutureIconUsesNoon() {
        ForecastHour noon = hour(day.plusDays(1), 12, 12.0, 4, 0);
        WeatherDisplay display = new WeatherDisplay(Arrays.asList(
                hour(day.minusDays(1), 12, 18.0, 1, 0),
                hour(day.plusDays(1), 9, 12.0, 1, 0), noon),
                LocalDateTime.of(day, java.time.LocalTime.NOON));
        assertFalse(display.days.containsKey(day.minusDays(1)));
        assertSame(noon, display.days.get(day.plusDays(1)).forecast);
    }
}
