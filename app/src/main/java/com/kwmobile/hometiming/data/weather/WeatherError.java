package com.kwmobile.hometiming.data.weather;

import androidx.annotation.StringRes;

import com.kwmobile.hometiming.R;

/** Why a forecast could not be loaded, with the Korean message to show. */
public enum WeatherError {
    /** The house has no region yet. */
    REGION_NOT_SET(R.string.weather_error_region_not_set),
    /** No KMA key in this build. */
    NOT_CONFIGURED(R.string.weather_error_not_configured),
    /** The key was refused or the API was not applied for on API허브. */
    NOT_AUTHORIZED(R.string.weather_error_not_authorized),
    NETWORK(R.string.weather_error_network),
    /** The run has not been published yet. */
    NO_DATA(R.string.weather_error_no_data),
    /** The response failed its checks, so nothing is judged from it. */
    INVALID_RESPONSE(R.string.weather_error_invalid_response),
    SERVER(R.string.weather_error_server);

    @StringRes
    public final int messageRes;

    WeatherError(@StringRes int messageRes) {
        this.messageRes = messageRes;
    }
}
