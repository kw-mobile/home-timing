package com.kwmobile.hometiming.data.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.kwmobile.hometiming.domain.BaseTimeCalculator;
import com.kwmobile.hometiming.domain.BaseTimeCalculator.BaseTime;

import java.io.IOException;
import java.time.LocalDateTime;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.junit.Test;

public class KmaForecastClientTest {
    private static final String KEY = "test-key-value";
    private static final BaseTime BASE = BaseTimeCalculator.latest(LocalDateTime.of(2026, 11, 11, 14, 10));

    private HttpUrl seen;

    private Interceptor answering(int code, String body) {
        return chain -> {
            seen = chain.request().url();
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("status")
                    .body(ResponseBody.create(body, MediaType.get("application/json")))
                    .build();
        };
    }

    private KmaForecastClient client(String key, Interceptor interceptor) {
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(interceptor).build();
        return new KmaForecastClient(key, http, KmaForecastClient.BASE_URL);
    }

    @Test
    public void requestsTheRunAndGridWithTheKey() throws WeatherException {
        String body = client(KEY, answering(200, "{\"ok\":1}")).fetch(BASE, 60, 127);

        assertEquals("{\"ok\":1}", body);
        assertEquals("apihub.kma.go.kr", seen.host());
        assertEquals("https", seen.scheme());
        assertEquals("/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst", seen.encodedPath());
        assertEquals(KEY, seen.queryParameter("authKey"));
        assertEquals("20261111", seen.queryParameter("base_date"));
        assertEquals("1400", seen.queryParameter("base_time"));
        assertEquals("60", seen.queryParameter("nx"));
        assertEquals("127", seen.queryParameter("ny"));
        assertEquals("JSON", seen.queryParameter("dataType"));
        assertEquals("2000", seen.queryParameter("numOfRows"));
    }

    @Test
    public void timesOutAfterTenSeconds() {
        assertEquals(10_000, KmaForecastClient.defaultHttpClient().callTimeoutMillis());
    }

    @Test
    public void aMissingKeyMakesNoRequest() {
        WeatherException e = assertThrows(WeatherException.class,
                () -> client("  ", answering(200, "{}")).fetch(BASE, 60, 127));

        assertEquals(WeatherError.NOT_CONFIGURED, e.error);
        assertNull(seen);
    }

    @Test
    public void mapsHttpStatus() {
        assertEquals(WeatherError.NOT_AUTHORIZED, failure(answering(401, "{}")).error);
        assertEquals(WeatherError.NOT_AUTHORIZED, failure(answering(403, "{}")).error);
        assertEquals(WeatherError.SERVER, failure(answering(500, "{}")).error);
    }

    @Test
    public void aNetworkFailureDoesNotCarryTheKey() {
        WeatherException e = failure(chain -> {
            throw new IOException("failed " + chain.request().url());
        });

        assertEquals(WeatherError.NETWORK, e.error);
        assertNull(e.getCause());
        assertFalse(e.toString().contains(KEY));
    }

    private WeatherException failure(Interceptor interceptor) {
        return assertThrows(WeatherException.class, () -> client(KEY, interceptor).fetch(BASE, 60, 127));
    }
}
