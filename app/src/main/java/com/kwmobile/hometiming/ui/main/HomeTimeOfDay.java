package com.kwmobile.hometiming.ui.main;

import java.time.ZonedDateTime;

/** Presentation periods, deliberately independent of weather and astronomical sunrise. */
enum HomeTimeOfDay {
    MORNING, DAY, EVENING, NIGHT;

    static HomeTimeOfDay at(ZonedDateTime time) {
        int hour = time.getHour();
        if (hour >= 6 && hour < 10) {
            return MORNING;
        }
        if (hour >= 10 && hour < 17) {
            return DAY;
        }
        if (hour >= 17 && hour < 20) {
            return EVENING;
        }
        return NIGHT;
    }
}
