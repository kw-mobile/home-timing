package com.kwmobile.hometiming.data.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.ForecastHour;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class WeatherLoaderTest {
    private static final LocalDate NOV_11 = LocalDate.of(2026, 11, 11);
    private static final LocalDate NOV_12 = NOV_11.plusDays(1);

    private final Map<String, String> stored = new HashMap<>();
    private final ForecastCache cache = new ForecastCache(new ForecastCache.Store() {
        @Override
        public String get(String key) {
            return stored.get(key);
        }

        @Override
        public void put(String key, String value) {
            stored.put(key, value);
        }
    });

    private int fetches;
    private WeatherError failWith;
    private String fixedBody;

    private final ForecastSource source = (base, nx, ny) -> {
        fetches++;
        if (failWith != null) {
            throw new WeatherException(failWith);
        }
        return fixedBody != null ? fixedBody : fullDay(base, nx, ny);
    };

    private static String fullDay(BaseTime base, int nx, int ny) {
        KmaJson json = new KmaJson(base, nx, ny);
        for (int hour = 0; hour < 24; hour++) {
            json.hour("20261112", String.format("%02d00", hour), "10", "0", "50");
        }
        return json.build();
    }

    private static Clock at(int day, int hour, int minute) {
        LocalDateTime seoul = LocalDateTime.of(2026, 11, day, hour, minute);
        return Clock.fixed(seoul.atZone(DateRules.ZONE).toInstant(), ZoneOffset.UTC);
    }

    private WeatherLoader loader(Clock clock) {
        return new WeatherLoader(source, cache, clock);
    }

    @Test
    public void fetchesThenServesTheSameRunFromCache() throws WeatherException {
        WeatherResult first = loader(at(11, 14, 10)).load(60, 127);
        WeatherResult second = loader(at(11, 16, 59)).load(60, 127);

        assertEquals(WeatherResult.Source.NETWORK, first.source);
        assertTrue(first.days.get(NOV_12).complete);
        assertEquals(WeatherResult.Source.CACHE, second.source);
        assertTrue(second.days.get(NOV_12).complete);
        assertEquals(first.fetchedAtMillis, second.fetchedAtMillis);
        assertEquals(1, fetches);
    }

    @Test
    public void aNewRunFetchesAgain() throws WeatherException {
        loader(at(11, 14, 10)).load(60, 127);

        WeatherResult next = loader(at(11, 17, 10)).load(60, 127);

        assertEquals(WeatherResult.Source.NETWORK, next.source);
        assertEquals(2, fetches);
    }

    @Test
    public void gridsAreCachedSeparately() throws WeatherException {
        loader(at(11, 14, 10)).load(60, 127);

        loader(at(11, 14, 11)).load(98, 76);

        assertEquals(2, fetches);
    }

    @Test
    public void aFailureFallsBackToACacheUpToSixHoursOld() throws WeatherException {
        loader(at(11, 14, 10)).load(60, 127);
        failWith = WeatherError.NETWORK;

        WeatherResult result = loader(at(11, 20, 10)).load(60, 127);

        assertEquals(WeatherResult.Source.STALE_CACHE, result.source);
        assertTrue(result.days.get(NOV_12).complete);
    }

    @Test
    public void aCacheOlderThanSixHoursIsNotUsed() throws WeatherException {
        loader(at(11, 14, 10)).load(60, 127);
        failWith = WeatherError.NETWORK;

        WeatherException e = assertThrows(WeatherException.class, () -> loader(at(11, 20, 11)).load(60, 127));

        assertEquals(WeatherError.NETWORK, e.error);
    }

    @Test
    public void aCacheFromTheFutureIsNotUsed() throws WeatherException {
        loader(at(11, 20, 10)).load(60, 127);
        failWith = WeatherError.NETWORK;

        assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(60, 127));
    }

    @Test
    public void aFailureWithoutACacheIsReported() {
        failWith = WeatherError.NOT_AUTHORIZED;

        WeatherException e = assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(60, 127));

        assertEquals(WeatherError.NOT_AUTHORIZED, e.error);
    }

    @Test
    public void anInvalidResponseIsNotCached() {
        fixedBody = "{}";

        WeatherException e = assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(60, 127));

        assertEquals(WeatherError.INVALID_RESPONSE, e.error);
        assertTrue(stored.isEmpty());
    }

    @Test
    public void aHouseWithoutARegionMakesNoRequest() {
        assertEquals(WeatherError.REGION_NOT_SET,
                assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(null, 127)).error);
        assertEquals(WeatherError.REGION_NOT_SET,
                assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(60, null)).error);
        assertEquals(WeatherError.REGION_NOT_SET,
                assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(0, 127)).error);
        assertEquals(WeatherError.REGION_NOT_SET,
                assertThrows(WeatherException.class, () -> loader(at(11, 14, 10)).load(150, 127)).error);
        assertEquals(0, fetches);
    }

    @Test
    public void cacheKeepsMissingValuesMissing() {
        BaseTime base = com.kwmobile.hometiming.domain.BaseTimeCalculator.latest(at(11, 14, 10));
        cache.write(60, 127, base, 5L, Collections.singletonList(new ForecastHour(NOV_12, 9, null, 0, 50)));

        ForecastCache.Entry entry = cache.read(60, 127);

        assertTrue(entry.isRun(base));
        assertEquals(5L, entry.fetchedAtMillis);
        assertNull(entry.hours.get(0).pop);
        assertEquals(Integer.valueOf(0), entry.hours.get(0).pty);
    }

    @Test
    public void aCorruptCacheReadsAsAbsent() {
        stored.put("forecast_60_127", "not json");

        assertNull(cache.read(60, 127));
    }

    @Test
    public void testModeUsesTheBundledForecastMarkedAsTest() throws WeatherException, IOException {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/test_forecast.json")),
                StandardCharsets.UTF_8);

        WeatherResult result = loader(at(11, 8, 0)).loadTest(json);

        assertEquals(WeatherResult.Source.TEST, result.source);
        assertEquals(0, fetches);
        assertTrue(result.days.get(NOV_11).complete);
        assertFalse(result.days.get(NOV_11).hasPrecip);
        assertTrue(result.days.get(NOV_12).hasPrecip);
        assertEquals(80, result.days.get(NOV_12).maxPop);
        assertFalse(result.days.get(NOV_11.plusDays(4)).complete);
    }

    @Test
    public void malformedTestForecastIsRefused() {
        assertEquals(WeatherError.INVALID_RESPONSE,
                assertThrows(WeatherException.class, () -> loader(at(11, 8, 0)).loadTest("{}")).error);
    }
}
