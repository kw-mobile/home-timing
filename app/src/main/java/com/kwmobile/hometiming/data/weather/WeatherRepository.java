package com.kwmobile.hometiming.data.weather;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.kwmobile.hometiming.BuildConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Forecasts for the house region and the region table (docs/MVP.md F-08·F-13). Failures carry a
 * {@link WeatherException}; they never affect item features, which do not use this class.
 */
public final class WeatherRepository {
    private static final String PREFS = "weather";
    private static final String PREF_TEST_MODE = "test_mode";
    private static final String REGIONS_ASSET = "regions.json";
    private static final String TEST_FORECAST_ASSET = "test_forecast.json";

    private static volatile WeatherRepository instance;

    private final AssetManager assets;
    private final SharedPreferences prefs;
    private final WeatherLoader loader;
    // One thread: a second request waits and then reads what the first one cached.
    private final Executor executor = Executors.newSingleThreadExecutor();

    private WeatherRepository(Context context) {
        assets = context.getAssets();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ForecastCache cache = new ForecastCache(new ForecastCache.Store() {
            @Override
            public String get(String key) {
                return prefs.getString(key, null);
            }

            @Override
            public void put(String key, String value) {
                prefs.edit().putString(key, value).apply();
            }
        });
        loader = new WeatherLoader(new KmaForecastClient(BuildConfig.KMA_SERVICE_KEY), cache, Clock.systemUTC());
    }

    @NonNull
    public static WeatherRepository getInstance(@NonNull Context context) {
        WeatherRepository local = instance;
        if (local == null) {
            synchronized (WeatherRepository.class) {
                local = instance;
                if (local == null) {
                    local = new WeatherRepository(context.getApplicationContext());
                    instance = local;
                }
            }
        }
        return local;
    }

    /** The forecast for a house's grid; in test weather mode, the bundled test forecast instead. */
    @NonNull
    public Task<WeatherResult> load(@Nullable Integer nx, @Nullable Integer ny) {
        return Tasks.call(executor, () -> isTestMode()
                ? loader.loadTest(readAsset(TEST_FORECAST_ASSET))
                : loader.load(nx, ny));
    }

    @NonNull
    public Task<List<Region>> regions() {
        return Tasks.call(executor, () -> RegionCatalog.parse(readAsset(REGIONS_ASSET)));
    }

    public boolean isTestMode() {
        return prefs.getBoolean(PREF_TEST_MODE, false);
    }

    public void setTestMode(boolean enabled) {
        prefs.edit().putBoolean(PREF_TEST_MODE, enabled).apply();
    }

    private String readAsset(String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
