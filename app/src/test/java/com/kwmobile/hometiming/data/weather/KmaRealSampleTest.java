package com.kwmobile.hometiming.data.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.BaseTimeCalculator;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.DailyForecast;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** A real API허브 response, run 2026-11-11 14:00, grid 60/127, stored unchanged (no key inside). */
public class KmaRealSampleTest {
    private static final BaseTime BASE = BaseTimeCalculator.latest(LocalDateTime.of(2026, 11, 11, 14, 10));
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    private static String sample() throws IOException {
        try (InputStream in = KmaRealSampleTest.class.getResourceAsStream("/kma/getVilageFcst_sample.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void parsesEveryForecastHour() throws IOException, WeatherException {
        List<ForecastHour> hours = KmaForecastParser.parse(sample(), BASE, 60, 127);

        assertEquals(66, hours.size());
        for (ForecastHour hour : hours) {
            assertTrue(hour.pop != null && hour.pty != null && hour.reh != null);
        }
    }

    @Test
    public void hourlyDaysAreJudgedAndThreeHourlyDaysAreUnknown() throws IOException, WeatherException {
        Map<LocalDate, DailyForecast> days =
                DailyForecast.summarize(KmaForecastParser.parse(sample(), BASE, 60, 127), TODAY, 14);

        assertTrue(days.get(TODAY).complete);
        assertTrue(days.get(TODAY.plusDays(1)).complete);
        assertTrue(days.get(TODAY.plusDays(2)).complete);
        // From the third day on KMA reports every 3 hours, so the 09–18 h window has gaps.
        assertFalse(days.get(TODAY.plusDays(3)).complete);
        assertFalse(days.get(TODAY.plusDays(4)).complete);
    }

    @Test
    public void refusesTheSampleForAnotherGrid() {
        WeatherError error = org.junit.Assert.assertThrows(WeatherException.class,
                () -> KmaForecastParser.parse(sample(), BASE, 61, 127)).error;

        assertEquals(WeatherError.INVALID_RESPONSE, error);
    }
}
