package com.kwmobile.hometiming.data.weather;

import com.kwmobile.hometiming.domain.BaseTimeCalculator;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;
import com.kwmobile.hometiming.domain.DailyForecast;
import com.kwmobile.hometiming.domain.DateRules;
import com.kwmobile.hometiming.domain.ForecastHour;
import com.kwmobile.hometiming.domain.HouseRules;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Picks the forecast to use: the stored latest run, else a fresh fetch, else on failure a stored
 * run no older than {@link #STALE_LIMIT_MILLIS}. Blocking; {@link WeatherRepository} runs it off
 * the main thread.
 */
final class WeatherLoader {
    static final long STALE_LIMIT_MILLIS = TimeUnit.HOURS.toMillis(6);

    private final ForecastSource source;
    private final ForecastCache cache;
    private final Clock clock;

    WeatherLoader(ForecastSource source, ForecastCache cache, Clock clock) {
        this.source = source;
        this.cache = cache;
        this.clock = clock;
    }

    WeatherResult load(Integer nx, Integer ny) throws WeatherException {
        if (nx == null || ny == null || nx < 1 || nx > HouseRules.NX_MAX || ny < 1 || ny > HouseRules.NY_MAX) {
            throw new WeatherException(WeatherError.REGION_NOT_SET);
        }
        BaseTime base = BaseTimeCalculator.latest(clock);
        ForecastCache.Entry stored = cache.read(nx, ny);
        if (stored != null && stored.isRun(base)) {
            return result(stored.hours, WeatherResult.Source.CACHE, stored.fetchedAtMillis);
        }
        try {
            List<ForecastHour> hours = KmaForecastParser.parse(source.fetch(base, nx, ny), base, nx, ny);
            long now = clock.millis();
            cache.write(nx, ny, base, now, hours);
            return result(hours, WeatherResult.Source.NETWORK, now);
        } catch (WeatherException e) {
            if (stored != null && isFreshEnough(stored)) {
                return result(stored.hours, WeatherResult.Source.STALE_CACHE, stored.fetchedAtMillis);
            }
            throw e;
        }
    }

    WeatherResult loadTest(String testForecastJson) throws WeatherException {
        List<ForecastHour> hours = TestForecast.hours(testForecastJson, DateRules.today(clock));
        return result(hours, WeatherResult.Source.TEST, clock.millis());
    }

    private boolean isFreshEnough(ForecastCache.Entry entry) {
        long age = clock.millis() - entry.fetchedAtMillis;
        return age >= 0 && age <= STALE_LIMIT_MILLIS;
    }

    private WeatherResult result(List<ForecastHour> hours, WeatherResult.Source from, long fetchedAtMillis) {
        LocalDate today = DateRules.today(clock);
        int currentHour = LocalDateTime.now(clock.withZone(DateRules.ZONE)).getHour();
        return new WeatherResult(DailyForecast.summarize(hours, today, currentHour), from, fetchedAtMillis);
    }
}
