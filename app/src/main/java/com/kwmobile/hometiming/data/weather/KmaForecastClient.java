package com.kwmobile.hometiming.data.weather;

import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Short-range forecast from KMA API허브. The key is added by an interceptor, so it is not part of
 * the call definition, and no HTTP logging is installed.
 */
final class KmaForecastClient implements ForecastSource {
    static final String BASE_URL = "https://apihub.kma.go.kr/";
    static final int TIMEOUT_SECONDS = 10;
    /** More than a full response holds; the parser refuses a truncated one through totalCount. */
    static final int ROWS = 2000;

    private interface Api {
        @GET("api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst")
        Call<ResponseBody> forecast(@Query("pageNo") int pageNo,
                                    @Query("numOfRows") int numOfRows,
                                    @Query("dataType") String dataType,
                                    @Query("base_date") String baseDate,
                                    @Query("base_time") String baseTime,
                                    @Query("nx") int nx,
                                    @Query("ny") int ny);
    }

    private final String key;
    private final Api api;

    KmaForecastClient(String key) {
        this(key, defaultHttpClient(), BASE_URL);
    }

    static OkHttpClient defaultHttpClient() {
        return new OkHttpClient.Builder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
    }

    KmaForecastClient(String key, OkHttpClient client, String baseUrl) {
        this.key = key == null ? "" : key.trim();
        OkHttpClient.Builder builder = client.newBuilder();
        builder.interceptors().add(0, this::addKey);
        this.api = new Retrofit.Builder().baseUrl(baseUrl).client(builder.build()).build().create(Api.class);
    }

    private okhttp3.Response addKey(Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        HttpUrl url = request.url().newBuilder().addQueryParameter("authKey", key).build();
        return chain.proceed(request.newBuilder().url(url).build());
    }

    @Override
    public String fetch(BaseTime base, int nx, int ny) throws WeatherException {
        if (key.isEmpty()) {
            throw new WeatherException(WeatherError.NOT_CONFIGURED);
        }
        try {
            Response<ResponseBody> response =
                    api.forecast(1, ROWS, "JSON", base.baseDate(), base.baseTime(), nx, ny).execute();
            if (response.code() == 401 || response.code() == 403) {
                throw new WeatherException(WeatherError.NOT_AUTHORIZED);
            }
            if (!response.isSuccessful()) {
                throw new WeatherException(WeatherError.SERVER);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new WeatherException(WeatherError.INVALID_RESPONSE);
            }
            return body.string();
        } catch (IOException e) {
            throw new WeatherException(WeatherError.NETWORK);
        }
    }
}
