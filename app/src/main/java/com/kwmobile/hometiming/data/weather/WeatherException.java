package com.kwmobile.hometiming.data.weather;

import androidx.annotation.NonNull;

/**
 * Failure of a forecast request. Deliberately carries no cause: a network exception can quote the
 * request URL, and the URL holds the API key.
 */
public final class WeatherException extends Exception {
    @NonNull
    public final WeatherError error;

    public WeatherException(@NonNull WeatherError error) {
        super(error.name());
        this.error = error;
    }
}
