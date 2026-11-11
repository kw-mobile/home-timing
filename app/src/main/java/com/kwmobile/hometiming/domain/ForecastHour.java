package com.kwmobile.hometiming.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One forecast hour as the weather layer parsed it. A value is null when the response did not
 * contain it, so missing data stays visible instead of looking like "no rain".
 */
public final class ForecastHour {
    public final LocalDate date;
    /** Hour of day, 0–23, in Asia/Seoul. */
    public final int hour;
    /** Precipitation probability in percent (KMA POP). */
    public final Integer pop;
    /** Precipitation type (KMA PTY): 0 none, anything else rain, sleet, snow or shower. */
    public final Integer pty;
    /** Relative humidity in percent (KMA REH). */
    public final Integer reh;

    public ForecastHour(LocalDate date, int hour, Integer pop, Integer pty, Integer reh) {
        this.date = Objects.requireNonNull(date, "date");
        this.hour = hour;
        this.pop = pop;
        this.pty = pty;
        this.reh = reh;
    }

    boolean isComplete() {
        return pop != null && pty != null && reh != null;
    }
}
