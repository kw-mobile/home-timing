package com.kwmobile.hometiming.data.weather;

import androidx.annotation.NonNull;

/**
 * Failure of a forecast request. Deliberately carries no cause: a network exception can quote the
 * request URL, and the URL holds the API key.
 */
public final class WeatherException extends Exception {
    @NonNull
    public final WeatherError error;
    /** Allowlisted provider status only; never a URL, key, body or cause. */
    public final String providerCode;

    public WeatherException(@NonNull WeatherError error) {
        this(error, null);
    }

    public WeatherException(@NonNull WeatherError error, String providerCode) {
        super(error.name());
        this.error = error;
        this.providerCode = providerCode != null
                && providerCode.matches("(?:(?:HTTP|HUB)_[0-9]{3}|KMA_[0-9]{2})") ? providerCode : null;
    }
}
