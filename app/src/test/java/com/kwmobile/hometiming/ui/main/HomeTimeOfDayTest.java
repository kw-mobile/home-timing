package com.kwmobile.hometiming.ui.main;

import static org.junit.Assert.assertEquals;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.Test;

public class HomeTimeOfDayTest {
    @Test
    public void transitionsAtExactLocalBoundaries() {
        check("05:59:59", HomeTimeOfDay.NIGHT);
        check("06:00:00", HomeTimeOfDay.MORNING);
        check("09:59:59", HomeTimeOfDay.MORNING);
        check("10:00:00", HomeTimeOfDay.DAY);
        check("16:59:59", HomeTimeOfDay.DAY);
        check("17:00:00", HomeTimeOfDay.EVENING);
        check("19:59:59", HomeTimeOfDay.EVENING);
        check("20:00:00", HomeTimeOfDay.NIGHT);
        check("23:59:59", HomeTimeOfDay.NIGHT);
        check("00:00:00", HomeTimeOfDay.NIGHT);
    }

    @Test
    public void sameInstantUsesTheProvidedLocalZone() {
        Instant instant = Instant.parse("2026-11-09T12:00:00Z");
        assertEquals(HomeTimeOfDay.NIGHT,
                HomeTimeOfDay.at(instant.atZone(ZoneId.of("Asia/Seoul"))));
        assertEquals(HomeTimeOfDay.DAY,
                HomeTimeOfDay.at(instant.atZone(ZoneId.of("UTC"))));
    }

    private void check(String time, HomeTimeOfDay expected) {
        assertEquals(expected, HomeTimeOfDay.at(
                ZonedDateTime.parse("2026-11-09T" + time + "+09:00[Asia/Seoul]")));
    }
}
