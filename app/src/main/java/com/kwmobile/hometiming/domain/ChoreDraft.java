package com.kwmobile.hometiming.domain;

import java.time.LocalDate;

/**
 * What the register/edit form submits for a weekly chore. Text is trimmed. Validate with
 * {@link ChoreInputValidator#validateDraft} before saving.
 */
public final class ChoreDraft {
    public static final String TYPE_BEDDING_WASH = "BEDDING_WASH";
    public static final String TYPE_OUTDOOR_DRY = "OUTDOOR_DRY";
    public static final String TYPE_VENTILATION = "VENTILATION";
    public static final String TYPE_CLEANING = "CLEANING";
    public static final String TYPE_ETC = "ETC";

    public final String name;
    public final String type;
    public final boolean weatherSensitive;
    /** 1 = Monday … 7 = Sunday; null until chosen. */
    public final Integer weekday;
    /** The first occurrence is the next such weekday after this date. */
    public final LocalDate startDate;
    public final String location;
    public final String memo;

    public ChoreDraft(String name, String type, boolean weatherSensitive, Integer weekday,
                      LocalDate startDate, String location, String memo) {
        this.name = trim(name);
        String picked = trim(type);
        this.type = picked.isEmpty() ? TYPE_ETC : picked;
        this.weatherSensitive = weatherSensitive;
        this.weekday = weekday;
        this.startDate = startDate;
        this.location = trim(location);
        this.memo = trim(memo);
    }

    /** Default for the form's weather switch: laundry-like chores are weather sensitive. */
    public static boolean defaultWeatherSensitive(String type) {
        return TYPE_BEDDING_WASH.equals(type) || TYPE_OUTDOOR_DRY.equals(type);
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
