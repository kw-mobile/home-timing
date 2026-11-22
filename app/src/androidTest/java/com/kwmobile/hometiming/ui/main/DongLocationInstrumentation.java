package com.kwmobile.hometiming.ui.main;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.kwmobile.hometiming.data.auth.AuthRepository;
import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.data.weather.CurrentPlaceResolver;
import com.kwmobile.hometiming.data.weather.Region;
import com.kwmobile.hometiming.domain.House;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** API36 dedicated AVD: mock GPS/addresses, optionally guarded real Firebase save/weather. */
@android.annotation.TargetApi(33)
public final class DongLocationInstrumentation extends Instrumentation {
    private static final String PASSWORD = "Demo-only-12345";
    private Bundle args;
    private MainActivity activity;
    private MvpViewModel live;
    private WeatherViewModel weather;
    private LocationManager manager;
    private String email;
    private final List<String> installed = new ArrayList<>();
    private final List<String> results = new ArrayList<>();
    private final AtomicInteger behavior = new AtomicInteger();
    private final AtomicReference<com.google.android.gms.tasks.TaskCompletionSource<List<Address>>> delayed = new AtomicReference<>();

    @Override public void onCreate(Bundle args) { this.args = args == null ? new Bundle() : args; super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle out = new Bundle(); boolean passed = false;
        try {
            check(android.os.Build.VERSION.SDK_INT >= 33 && ("ranchu".equals(android.os.Build.HARDWARE)
                    || "goldfish".equals(android.os.Build.HARDWARE)), "dedicated modern AVD required");
            if ("identify".equals(args.getString("mode"))) {
                check(FirebaseApp.getInstance().getOptions().getProjectId().equals(args.getString("projectId")), "explicit project guard");
                com.google.firebase.auth.FirebaseUser own = FirebaseAuth.getInstance().getCurrentUser();
                check(own != null && own.getEmail() != null && own.getEmail().matches("mvp-production-dong-[0-9]{6}-location@example\\.test"), "only this runner's temporary identity may be reported");
                String ownRun = own.getEmail().split("-location@")[0];
                passed = true; out.putString("stream", "DONG OWN RUN " + ownRun + "\n"); finish(Activity.RESULT_OK, out); return;
            }
            boolean configured = "configured".equals(args.getString("service"));
            if (configured) {
                String run = args.getString("runId", "");
                check(run.matches("mvp-production-[a-zA-Z0-9_-]{1,25}")
                        && FirebaseApp.getInstance().getOptions().getProjectId().equals(args.getString("projectId")), "explicit project/run guards");
                email = run + "-location@example.test";
            }
            if ("cleanup".equals(args.getString("mode"))) {
                check(configured && FirebaseAuth.getInstance().getCurrentUser() != null
                        && email.equals(FirebaseAuth.getInstance().getCurrentUser().getEmail()), "exact own interrupted test account required");
                try {
                    Tasks.await(FirebaseAuth.getInstance().getCurrentUser().reload(), 30, TimeUnit.SECONDS);
                    Tasks.await(AuthRepository.getInstance().deleteAccount(PASSWORD), 90, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (!(cause instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException)
                            || !"ERROR_USER_NOT_FOUND".equals(((com.google.firebase.auth.FirebaseAuthInvalidUserException) cause).getErrorCode())) throw error;
                }
                AuthRepository.getInstance().signOut();
                check(FirebaseAuth.getInstance().getCurrentUser() == null, "own account cleanup complete");
                passed = true; out.putString("stream", "DONG LOCATION CLEANUP PASS: exact guarded account only\n");
                finish(Activity.RESULT_OK, out); return;
            }
            check(FirebaseAuth.getInstance().getCurrentUser() == null, "no existing account allowed");
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(() -> {
                live = new ViewModelProvider(activity).get(MvpViewModel.class);
                weather = new ViewModelProvider(activity).get(WeatherViewModel.class);
                if (configured) live.authenticate("signup", "LocationProbe", email, PASSWORD);
                else {
                    live.session = new Session("location-probe", "location-probe", "Probe", false);
                    live.house = new House("location-probe", "location-probe", 1, 0, 0, null, null, null, null);
                    for (String name : new String[]{"itemCache", "choreCache", "houseCache"}) set(live, name, false);
                }
            });
            await(() -> live.session != null && live.house != null && !live.readOnly() && !live.busy(), 90000, "online house ready");
            runOnMainSync(() -> {
                new ViewModelProvider(activity).get(DesignPreviewViewModel.class).navigate("preferences", "region");
                try (java.io.InputStream input = getTargetContext().getAssets().open("regions.json")) {
                    JSONArray rows = new JSONObject(new java.io.BufferedReader(new java.io.InputStreamReader(input,
                            java.nio.charset.StandardCharsets.UTF_8)).lines().collect(java.util.stream.Collectors.joining("\n"))).getJSONArray("regions");
                    List<Region> regions = new ArrayList<>();
                    for (int i = 0; i < rows.length(); i++) { JSONObject r = rows.getJSONObject(i); regions.add(Region.of(r.getString("name"), r.getInt("nx"), r.getInt("ny"))); }
                    set(weather, "regions", regions);
                } catch (Exception e) { throw new RuntimeException(e); }
                invoke("render");
                if (!"backend".equals(args.getString("mode"))) {
                    CurrentPlaceResolver.Lookup fake = (lat, lon) -> {
                        if (behavior.get() == 1) return Tasks.forException(new java.io.IOException("synthetic unavailable"));
                        if (behavior.get() == 2) {
                            com.google.android.gms.tasks.TaskCompletionSource<List<Address>> pending = new com.google.android.gms.tasks.TaskCompletionSource<>();
                            delayed.set(pending); return pending.getTask();
                        }
                        return Tasks.forResult(Collections.singletonList(address()));
                    };
                    set(activity.houseLocation, "placeResolver", new CurrentPlaceResolver(fake));
                }
            });
            if ("backend".equals(args.getString("mode"))) {
                Region result = Tasks.await(new CurrentPlaceResolver(new Geocoder(getTargetContext(), Locale.KOREA))
                        .resolve(37.5665, 126.9780, weather.regions()), 12, TimeUnit.SECONDS);
                results.add("PASS actual Android Geocoder for public Seoul sample: " + result.name + ", grid=" + result.nx + "," + result.ny);
            } else {
                manager = getTargetContext().getSystemService(LocationManager.class);
                for (String provider : new String[]{"fused", "network", "gps"}) addProvider(provider);
                // First use: no preloaded region table. One click must continue after the asset read.
                runOnMainSync(() -> set(weather, "regions", Collections.emptyList()));
                request(); feed("gps", 30);
                await(() -> visible().contains("집 위치 확인") && visible().contains("태평로1가"), 10000, "GPS -> dong confirmation without district list");
                screenshot("dong-confirm.png");
                if (configured) {
                    check(click("이 위치로 설정"), "confirm current house location");
                    await(() -> live.house != null && "서울특별시 중구 태평로1가".equals(live.house.regionName) && !live.busy() && !live.readOnly(), 30000, "real Firebase dong region saved");
                    await(() -> "서울특별시 중구 태평로1가".equals(weather.regionName()) && weather.current() != null
                            && !weather.current().loading && weather.current().result != null && weather.current().error == null, 45000, "weather automatically refreshes from saved house");
                    check(!weather.isTestMode() && live.house.nx == 60 && live.house.ny == 127, "real forecast and received GPS grid");
                    com.google.firebase.firestore.DocumentSnapshot saved = Tasks.await(
                            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("houses").document(live.session.houseId)
                                    .get(com.google.firebase.firestore.Source.SERVER), 30, TimeUnit.SECONDS);
                    check("서울특별시 중구 태평로1가".equals(saved.getString("regionName"))
                            && Long.valueOf(60).equals(saved.getLong("nx")) && Long.valueOf(127).equals(saved.getLong("ny")), "independent server readback");
                    check(weather.current().result.source != com.kwmobile.hometiming.data.weather.WeatherResult.Source.STALE_CACHE,
                            "actual weather refresh, not a failed-request stale fallback");
                    results.add("PASS mock GPS + synthetic dong address -> confirmed real Firebase house save/server readback -> automatic KMA weather source="
                            + weather.current().result.source + ", hours=" + weather.current().result.hours.size());
                    runOnMainSync(() -> { new ViewModelProvider(activity).get(DesignPreviewViewModel.class).tab("home"); invoke("render"); });
                    waitForIdleSync(); screenshot("dong-home.png");
                    runOnMainSync(() -> { new ViewModelProvider(activity).get(DesignPreviewViewModel.class).navigate("weather", ""); invoke("render"); });
                    waitForIdleSync(); screenshot("dong-weather.png");
                } else {
                    back(); check(live.house.regionName == null, "cancel keeps shared house unchanged");
                    results.add("PASS GPS-only accurate fix + synthetic address -> dong confirmation; cancel keeps region");
                    reset(); behavior.set(0); request(); feed("network", 2500);
                    check(!visible().contains("집 위치 확인"), "low accuracy does not claim dong");
                    feed("gps", 30); await(() -> visible().contains("태평로1가"), 5000, "later accurate GPS replaces broad network fix");
                    back(); results.add("PASS waits for accurate GPS instead of naming a dong from broad network fix");
                    reset(); behavior.set(1); request(); feed("gps", 30);
                    await(() -> visible().contains("주변 시·군·구 선택"), 5000, "geocoder error district fallback");
                    back(); results.add("PASS address service failure -> district fallback");
                    reset(); behavior.set(2); request(); feed("gps", 30);
                    await(() -> visible().contains("주변 시·군·구 선택"), 11000, "8s address timeout fallback");
                    com.google.android.gms.tasks.TaskCompletionSource<List<Address>> late = delayed.get(); check(late != null, "address request in flight");
                    late.trySetResult(Collections.singletonList(address())); SystemClock.sleep(700);
                    check(!visible().contains("집 위치 확인"), "late address after timeout cannot replace fallback");
                    back(); results.add("PASS 8s address timeout -> district fallback; late address ignored");
                    reset(); behavior.set(2); delayed.set(null); request(); feed("gps", 30);
                    await(() -> delayed.get() != null, 3000, "address pending before background");
                    getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
                    await(() -> !activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED), 8000, "activity actually stopped");
                    check(!activity.houseLocation.active(), "onStop clears pending location/address");
                    delayed.get().trySetResult(Collections.singletonList(address()));
                    getTargetContext().startActivity(new Intent(getTargetContext(), MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    await(() -> activity.getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED), 8000, "activity resumed");
                    waitForIdleSync(); SystemClock.sleep(300);
                    check(!visible().contains("집 위치 확인"), "background stop discards late address");
                    results.add("PASS stop/background discards late address");

                    reset(); behavior.set(2); delayed.set(null); request(); feed("gps", 30);
                    await(() -> delayed.get() != null, 3000, "delayed address captured");
                    runOnMainSync(() -> live.session = new Session("other", "other", "Other", false));
                    delayed.get().trySetResult(Collections.singletonList(address())); SystemClock.sleep(700);
                    check(!visible().contains("집 위치 확인"), "account switch rejects stale dong");
                    results.add("PASS session change rejects late dong address; no Firebase writes");
                }
            }
            passed = true;
        } catch (Throwable error) { results.add("FAIL " + error + " visible=" + visible()); android.util.Log.e("DongLocationTest", "Failed", error); }
        finally {
            if (manager != null) for (String provider : installed) try { manager.removeTestProvider(provider); } catch (Exception ignored) { }
            if (email != null && FirebaseAuth.getInstance().getCurrentUser() != null
                    && email.equals(FirebaseAuth.getInstance().getCurrentUser().getEmail())) {
                try {
                    if (live == null) throw new AssertionError("test model required for subscription-safe cleanup");
                    runOnMainSync(() -> live.deleteAccount(PASSWORD));
                    await(() -> FirebaseAuth.getInstance().getCurrentUser() == null && live.session == null, 90000, "own account/house and local auth cleanup");
                    results.add("PASS own temporary account/house and local auth cleanup");
                }
                catch (Exception cleanup) { passed = false; results.add("FAIL own cleanup " + cleanup); }
            }
            if (activity != null) runOnMainSync(activity::finish);
        }
        out.putString("stream", String.join("\n", results) + "\nDONG LOCATION " + (passed ? "PASS" : "FAIL") + ": mock GPS is not physical reception\n");
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, out);
    }
    private Address address() {
        Address value = new Address(Locale.KOREA); value.setCountryCode("KR"); value.setAdminArea("서울특별시");
        value.setLocality("중구"); value.setSubLocality("태평로1가");
        value.setAddressLine(0, "대한민국 서울특별시 중구 태평로1가 세종대로 110"); return value;
    }
    private void addProvider(String provider) {
        manager.addTestProvider(provider, false, false, false, false, true, true, true, android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE);
        manager.setTestProviderEnabled(provider, true); if (!installed.contains(provider)) installed.add(provider);
    }
    private void reset() { for (String provider : installed) { manager.removeTestProvider(provider); addProvider(provider); } }
    private void request() { runOnMainSync(() -> activity.houseLocation.request()); }
    private void feed(String provider, float accuracy) {
        Location value = new Location(provider); value.setLatitude(37.5665); value.setLongitude(126.9780);
        value.setAccuracy(accuracy); value.setTime(System.currentTimeMillis()); value.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        manager.setTestProviderLocation(provider, value); SystemClock.sleep(300);
    }
    private void back() {
        getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
        await(() -> !visible().contains("집 위치 확인") && !visible().contains("주변 시·군·구 선택"), 5000, "dialog dismissed");
        waitForIdleSync();
    }
    private String visible() { StringBuilder result = new StringBuilder(); collect(getUiAutomation().getRootInActiveWindow(), result); return result.toString(); }
    private void collect(AccessibilityNodeInfo node, StringBuilder out) {
        if (node == null) return; if (node.getText() != null) out.append(node.getText()).append('\n');
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), out);
    }
    private boolean click(String label) { return clickNode(getUiAutomation().getRootInActiveWindow(), label); }
    private boolean clickNode(AccessibilityNodeInfo node, String label) {
        if (node == null) return false; if (label.contentEquals(node.getText() == null ? "" : node.getText())) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) if (clickNode(node.getChild(i), label)) return true; return false;
    }
    private void screenshot(String name) throws java.io.IOException {
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot(); check(bitmap != null, "screenshot");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(), name))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); }
        finally { bitmap.recycle(); }
    }
    private void invoke(String name) { try { java.lang.reflect.Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity); } catch (Exception e) { throw new RuntimeException(e); } }
    private static void set(Object object, String name, Object value) { try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); } catch (Exception e) { throw new RuntimeException(e); } }
    private void await(BooleanSupplier condition, long timeout, String label) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        check(condition.getAsBoolean(), label);
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
