package com.kwmobile.hometiming.ui.main;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.data.weather.Region;
import com.kwmobile.hometiming.data.weather.WeatherError;
import com.kwmobile.hometiming.data.weather.WeatherException;
import com.kwmobile.hometiming.data.weather.WeatherRepository;
import com.kwmobile.hometiming.data.weather.WeatherResult;
import java.util.Collections;
import java.util.List;

/** Weather only. Item/house preview state is not sent to a remote service. */
public final class WeatherViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean loading;
        public final WeatherResult result;
        public final WeatherError error;
        public final String providerCode;
        State(boolean loading, WeatherResult result, WeatherError error) {
            this(loading, result, error, null);
        }
        State(boolean loading, WeatherResult result, WeatherError error, String providerCode) {
            this.loading = loading; this.result = result; this.error = error; this.providerCode = providerCode;
        }
    }

    private final WeatherRepository repository;
    private final SharedPreferences preferences;
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(true, null, null));
    private List<Region> regions = Collections.emptyList();
    private String regionName;
    private Region houseRegion;
    private boolean sharedRegionRequired;
    private boolean resolvingRegions, requesting, disposed;
    private int generation;
    private long requestedAt;
    private String publishedRegion;
    private boolean publishedTestMode;

    public WeatherViewModel(@NonNull Application application) {
        super(application);
        repository = WeatherRepository.getInstance(application);
        preferences = application.getSharedPreferences("weather_ui", 0);
        regionName = preferences.getString("region", "서울특별시 마포구");
        publishedRegion = regionName;
        publishedTestMode = repository.isTestMode();
    }

    public LiveData<State> state() { return state; }
    public State current() { return state.getValue(); }
    public String regionName() { return regionName; }
    public boolean isTestMode() { return repository.isTestMode(); }
    public String[] regionNames() {
        String[] names = new String[regions.size()];
        for (int i = 0; i < names.length; i++) names[i] = regions.get(i).name;
        return names;
    }

    List<Region> nearbyRegions(double latitude, double longitude) {
        return com.kwmobile.hometiming.data.weather.RegionLocationMatcher.nearby(latitude, longitude, regions);
    }

    /** Assets are local, but the first button click must wait for their asynchronous read. */
    public com.google.android.gms.tasks.Task<List<Region>> prepareRegions() {
        if (!regions.isEmpty()) return com.google.android.gms.tasks.Tasks.forResult(regions);
        return repository.regions().continueWith(task -> {
            if (disposed) throw new IllegalStateException("Weather screen disposed");
            if (!task.isSuccessful()) throw new java.io.IOException("Region catalog unavailable");
            regions = task.getResult();
            if (regions.isEmpty()) throw new java.io.IOException("Empty region catalog");
            return regions;
        });
    }

    public List<Region> regions() { return java.util.Collections.unmodifiableList(regions); }

    public Region findRegion(String name) {
        for (Region value : regions) if (value.name.equals(name)) return value;
        return null;
    }

    public void useSharedRegion(com.kwmobile.hometiming.domain.House house) {
        sharedRegionRequired = true;
        if (house == null || !house.hasRegion()) {
            if (houseRegion == null && current() != null && current().error == WeatherError.REGION_NOT_SET) return;
            houseRegion = null;
            regionName = getApplication().getString(com.kwmobile.hometiming.R.string.mvp_region_unset);
            invalidate();
            publish(new State(false, null, WeatherError.REGION_NOT_SET));
            return;
        }
        if (houseRegion != null && houseRegion.name.equals(house.regionName)
                && houseRegion.nx == house.nx && houseRegion.ny == house.ny) return;
        houseRegion = Region.of(house.regionName, house.nx, house.ny);
        regionName = house.regionName;
        invalidate(); refresh(true);
    }

    public void selectRegion(String name) {
        for (Region region : regions) if (region.name.equals(name)) {
            regionName = name;
            preferences.edit().putString("region", name).apply();
            invalidate();
            refresh(true);
            return;
        }
    }

    public void setTestMode(boolean enabled) {
        repository.setTestMode(enabled);
        invalidate();
        refresh(true);
    }

    public WeatherDisplay display() {
        State value = current();
        return value == null || value.result == null ? null :
                new WeatherDisplay(value.result.hours, java.time.LocalDateTime.now(com.kwmobile.hometiming.domain.DateRules.ZONE));
    }

    public java.util.Map<java.time.LocalDate, com.kwmobile.hometiming.domain.DailyForecast> days() {
        State value = current();
        if (value == null || value.result == null) return Collections.emptyMap();
        java.time.LocalDateTime now = java.time.LocalDateTime.now(com.kwmobile.hometiming.domain.DateRules.ZONE);
        return com.kwmobile.hometiming.domain.DailyForecast.summarize(value.result.hours, now.toLocalDate(), now.getHour());
    }

    public com.kwmobile.hometiming.domain.LaundryAdvisor.Verdict verdict(java.time.LocalDate date) {
        return com.kwmobile.hometiming.domain.LaundryAdvisor.judge(days().get(date));
    }

    public java.time.LocalDate proposalTarget(java.time.LocalDate due) {
        java.time.LocalDate today = com.kwmobile.hometiming.domain.DateRules.today(java.time.Clock.systemUTC());
        if (due.isBefore(today) || verdict(due) != com.kwmobile.hometiming.domain.LaundryAdvisor.Verdict.BAD) return null;
        return com.kwmobile.hometiming.domain.LaundryAdvisor.findAlternative(due, days(), today);
    }

    private void publish(State value) {
        State previous = current();
        boolean testMode = repository.isTestMode();
        if (previous != null && previous.loading == value.loading && previous.result == value.result
                && previous.error == value.error && java.util.Objects.equals(previous.providerCode, value.providerCode)
                && java.util.Objects.equals(publishedRegion, regionName)
                && publishedTestMode == testMode) return;
        publishedRegion = regionName;
        publishedTestMode = testMode;
        state.setValue(value);
    }

    private void invalidate() {
        generation++;
        requesting = false;
        requestedAt = 0;
        publish(new State(true, null, null));
    }

    public void refresh(boolean force) {
        if (disposed) return;
        if (regions.isEmpty()) {
            if (resolvingRegions) return;
            resolvingRegions = true;
            publish(new State(true, null, null));
            repository.regions().addOnSuccessListener(values -> {
                resolvingRegions = false;
                if (disposed) return;
                regions = values;
                if (regions.isEmpty()) { publish(new State(false, null, WeatherError.REGION_NOT_SET)); return; }
                refresh(true);
            }).addOnFailureListener(error -> {
                resolvingRegions = false;
                if (!disposed) publish(new State(false, null, WeatherError.REGION_NOT_SET));
            });
            return;
        }
        if (sharedRegionRequired && houseRegion == null) {
            publish(new State(false, null, WeatherError.REGION_NOT_SET)); return;
        }
        if (requesting) return;
        State previous = current();
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && previous != null && previous.error == WeatherError.NOT_CONFIGURED) return;
        if (!force && requestedAt > 0 && now - requestedAt < 300_000) return;
        Region selected = houseRegion;
        if (!sharedRegionRequired) for (Region region : regions) if (region.name.equals(regionName)) { selected = region; break; }
        if (selected == null) { publish(new State(false, null, WeatherError.REGION_NOT_SET)); return; }
        requesting = true;
        requestedAt = now;
        int request = ++generation;
        publish(new State(true, previous == null ? null : previous.result, null));
        repository.load(selected.nx, selected.ny).addOnSuccessListener(result -> {
            if (disposed || request != generation) return;
            requesting = false;
            publish(new State(false, result, null));
        }).addOnFailureListener(error -> {
            if (disposed || request != generation) return;
            requesting = false;
            WeatherError reason = error instanceof WeatherException
                    ? ((WeatherException) error).error : WeatherError.NETWORK;
            publish(new State(false, null, reason,
                    error instanceof WeatherException ? ((WeatherException) error).providerCode : null));
        });
    }

    @Override protected void onCleared() { disposed = true; generation++; }
}
