package com.kwmobile.hometiming.data.weather;

import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;

/** Where the raw forecast response comes from. */
interface ForecastSource {
    String fetch(BaseTime base, int nx, int ny) throws WeatherException;
}
