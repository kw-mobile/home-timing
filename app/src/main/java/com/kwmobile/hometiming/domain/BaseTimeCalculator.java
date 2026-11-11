package com.kwmobile.hometiming.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * The latest published short-range forecast run: KMA publishes at 02, 05, 08, 11, 14, 17, 20 and
 * 23 h, and a run becomes available about 10 minutes later. Also the weather cache key.
 */
public final class BaseTimeCalculator {
    /** A forecast run, in the request format: {@code base_date} yyyyMMdd and {@code base_time} HHmm. */
    public static final class BaseTime {
        private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd", Locale.ROOT);

        public final LocalDate date;
        public final int hour;

        BaseTime(LocalDate date, int hour) {
            this.date = date;
            this.hour = hour;
        }

        public String baseDate() {
            return DATE.format(date);
        }

        public String baseTime() {
            return String.format(Locale.ROOT, "%02d00", hour);
        }
    }

    static final int PUBLISH_DELAY_MINUTES = 10;
    private static final int FIRST_RUN_HOUR = 2;
    private static final int RUN_INTERVAL_HOURS = 3;
    private static final int LAST_RUN_HOUR = 23;

    private BaseTimeCalculator() {
    }

    /** The latest available run at this Asia/Seoul local time. */
    public static BaseTime latest(LocalDateTime seoulNow) {
        Objects.requireNonNull(seoulNow, "seoulNow");
        LocalDateTime available = seoulNow.minusMinutes(PUBLISH_DELAY_MINUTES);
        if (available.getHour() < FIRST_RUN_HOUR) {
            return new BaseTime(available.toLocalDate().minusDays(1), LAST_RUN_HOUR);
        }
        int runs = (available.getHour() - FIRST_RUN_HOUR) / RUN_INTERVAL_HOURS;
        return new BaseTime(available.toLocalDate(), FIRST_RUN_HOUR + runs * RUN_INTERVAL_HOURS);
    }

    public static BaseTime latest(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return latest(LocalDateTime.now(clock.withZone(DateRules.ZONE)));
    }
}
