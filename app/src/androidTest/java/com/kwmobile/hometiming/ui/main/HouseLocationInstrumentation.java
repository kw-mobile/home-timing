package com.kwmobile.hometiming.ui.main;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.ViewModelProvider;
import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.data.weather.Region;
import com.kwmobile.hometiming.domain.House;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Dedicated AVD only: real OS mock providers and UI callbacks; never saves a Firebase house. */
public final class HouseLocationInstrumentation extends Instrumentation {
    private MainActivity activity;
    private String mode;
    private MvpViewModel live;
    private LocationManager manager;
    private final List<String> installed = new ArrayList<>();
    private final List<String> results = new ArrayList<>();

    @Override public void onCreate(Bundle args) { mode = args == null ? "providers" : args.getString("mode", "providers"); super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); boolean success = false;
        try {
            if (!"goldfish".equals(android.os.Build.HARDWARE) && !"ranchu".equals(android.os.Build.HARDWARE))
                throw new AssertionError("Dedicated emulator required");
            if (com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null)
                throw new AssertionError("Fresh dedicated installation with no account required");
            activity = (MainActivity) startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            manager = getTargetContext().getSystemService(LocationManager.class);
            runOnMainSync(() -> {
                live = new ViewModelProvider(activity).get(MvpViewModel.class);
                live.session = new Session("location-probe", "location-probe", "Probe", false);
                live.house = new House("location-probe", "location-probe", 1, 0, 0, null, null, null, null);
                for (String name : new String[]{"itemCache", "choreCache", "houseCache"}) set(live, name, false);
                DesignPreviewViewModel ui = new ViewModelProvider(activity).get(DesignPreviewViewModel.class);
                ui.navigate("preferences", "region");
                WeatherViewModel weather = new ViewModelProvider(activity).get(WeatherViewModel.class);
                try (java.io.InputStream input = getTargetContext().getAssets().open("regions.json")) {
                    JSONArray values = new JSONObject(new java.io.BufferedReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).lines().collect(java.util.stream.Collectors.joining("\n")))
                            .getJSONArray("regions");
                    List<Region> regions = new ArrayList<>();
                    for (int i = 0; i < values.length(); i++) {
                        JSONObject r = values.getJSONObject(i);
                        regions.add(Region.of(r.getString("name"), r.getInt("nx"), r.getInt("ny")));
                    }
                    set(weather, "regions", regions);
                } catch (Exception e) { throw new RuntimeException(e); }
                invoke("render");
            });
            waitForIdleSync();
            if ("denied".equals(mode)) {
                runOnMainSync(() -> invoke("requestHouseLocation"));
                await(() -> visible().contains("위치 권한 요청") || visible().contains("Don’t allow")
                        || visible().contains("Don't allow"), 10000, "permission prompt or rationale");
                if (visible().contains("위치 권한 요청")) {
                    check(clickExact(getUiAutomation().getRootInActiveWindow(), "위치 권한 요청"), "repeat-request rationale confirmation");
                }
                await(() -> visible().contains("Don’t allow") || visible().contains("Don't allow"), 10000, "runtime permission prompt");
                check(clickDeny(getUiAutomation().getRootInActiveWindow()), "deny permission button");
                await(() -> visible().contains("위치 권한이 필요해요"), 5000, "permission denial 안내");
                check(!active(), "denied permission does not start location");
                pass("runtime permission denial -> manual-selection 안내; no request");
            } else if ("disabled".equals(mode)) {
                check(!androidx.core.location.LocationManagerCompat.isLocationEnabled(manager), "location disabled by dedicated AVD setup");
                triggerRequest();
                await(() -> visible().contains("휴대폰 위치를 켜거나"), 5000, "location disabled 안내");
                check(!active(), "disabled location does not start request");
                pass("location disabled -> manual-selection 안내; no request");
            } else if ("manual".equals(mode)) {
                runOnMainSync(() -> { set(new ViewModelProvider(activity).get(WeatherViewModel.class), "regions", java.util.Collections.emptyList()); invoke("chooseHouseRegion"); });
                await(() -> visible().contains("시·도 선택"), 5000, "province picker");
                check(clickExact(getUiAutomation().getRootInActiveWindow(), "서울특별시"), "pick Seoul province");
                await(() -> visible().contains("시·군·구 선택"), 5000, "district picker");
                check(clickExact(getUiAutomation().getRootInActiveWindow(), "서울특별시 강남구"), "pick district");
                await(() -> visible().contains("집 위치 확인") && visible().contains("서울특별시 강남구"), 5000, "district confirmation");
                screenshot("manual-district-confirm.png");
                getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                check(live.house.regionName == null, "manual confirmation cancel leaves shared region unchanged");
                pass("province -> district -> confirmation; cancel preserves shared region");
            } else {
            for (String provider : new String[]{"fused", "network"}) {
                manager.addTestProvider(provider, false, false, false, false, true, true, true,
                        android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_COARSE);
                installed.add(provider); manager.setTestProviderEnabled(provider, true);
            }
            request();
            feed("network", 37.5665, 126.9780);
            await(() -> visible().contains("주변 시·군·구 선택") && visible().contains("서울특별시"), 10000, "network-only fix selects Seoul candidates");
            screenshot("seoul-candidates.png");
            String first = visible();
            check(!first.contains("부산광역시"), "Seoul fix excludes Busan");
            getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            waitForIdleSync();
            check(live.house.regionName == null, "cancel does not save shared region");
            pass("network-only fresh fix -> Seoul; cancel leaves house unchanged");

            // Remove/reinstall providers to clear their cached fixes before testing fused alone.
            resetProviders(); request(); feed("fused", 35.1796, 129.0756);
            await(() -> visible().contains("주변 시·군·구 선택") && visible().contains("부산광역시"), 10000, "fused-only fix selects Busan candidates");
            getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            waitForIdleSync(); pass("fused-only fresh fix -> Busan");

            resetProviders(); request(); feed("network", 35.6762, 139.6503, 1); feed("fused", 35.6762, 139.6503, 1);
            await(() -> visible().contains("현재 위치 주변에 예보 지역 후보가 없어요"), 5000, "outside Korea has no candidate");
            check(!active(), "unsupported location releases request");
            pass("outside covered grids -> no-region 안내");

            resetProviders(); request(); long started = SystemClock.elapsedRealtime();
            await(() -> visible().contains("휴대폰에서 현재 위치를 받지 못했어요"), 23000, "20s no-fix timeout 안내");
            check(!active() && SystemClock.elapsedRealtime() - started < 22500, "timeout releases providers");
            pass("no provider fix -> 20s timeout and manual-selection 안내");

            resetProviders(); request();
            runOnMainSync(() -> live.session = new Session("other", "other", "Other", false));
            feed("network", 37.5665, 126.9780);
            SystemClock.sleep(1500);
            check(!visible().contains("주변 시·군·구 선택"), "late fix after account switch rejected");
            check(live.house.regionName == null, "no server-save call by test");
            pass("late callback after session switch rejected; no Firebase mutation");
            }
            success = true;
        } catch (Throwable e) { results.add("FAIL " + e.toString() + " visible=" + visible()); android.util.Log.e("HouseLocationTest", "Scenario failed", e); }
        finally {
            if (manager != null) for (String provider : installed) {
                try { manager.removeTestProvider(provider); } catch (Exception ignored) { }
            }
            if (activity != null) runOnMainSync(activity::finish);
        }
        result.putString("stream", String.join("\n", results) + "\nHOUSE LOCATION " + (success ? "PASS" : "FAIL")
                + ": OS mock providers, not physical GPS reception\n");
        finish(success ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private boolean clickExact(AccessibilityNodeInfo node, String label) {
        if (node == null) return false;
        if (label.contentEquals(node.getText() == null ? "" : node.getText()))
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) if (clickExact(node.getChild(i), label)) return true;
        return false;
    }
    private boolean clickDeny(AccessibilityNodeInfo node) {
        if (node == null) return false;
        String text = node.getText() == null ? "" : node.getText().toString();
        if (text.equals("Don’t allow") || text.equals("Don't allow")) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        for (int i = 0; i < node.getChildCount(); i++) if (clickDeny(node.getChild(i))) return true;
        return false;
    }
    private boolean active() {
        java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean();
        runOnMainSync(() -> {
            try { Field f = MainActivity.class.getDeclaredField("houseLocationRequest"); f.setAccessible(true); result.set(f.get(activity) != null); }
            catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        });
        return result.get();
    }
    private void screenshot(String name) {
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("screenshot unavailable");
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(), name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output);
        } catch (java.io.IOException e) { throw new RuntimeException(e); }
        finally { bitmap.recycle(); }
    }
    private void resetProviders() {
        for (String p : installed) {
            manager.removeTestProvider(p);
            manager.addTestProvider(p, false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_COARSE);
            manager.setTestProviderEnabled(p, true);
        }
    }
    private void triggerRequest() {
        runOnMainSync(() -> invoke("requestHouseLocation"));
        await(() -> active() || visible().contains("구 단위로 찾기") || visible().contains("휴대폰 위치를 켜거나"), 5000, "request or permission choice ready");
        if (visible().contains("구 단위로 찾기")) {
            check(clickExact(getUiAutomation().getRootInActiveWindow(), "구 단위로 찾기"), "keep approximate permission");
            waitForIdleSync();
        }
    }
    private void request() { triggerRequest(); await(this::active, 5000, "location request started"); }
    private void feed(String provider, double lat, double lon) { feed(provider, lat, lon, 4); }
    private void feed(String provider, double lat, double lon, int count) {
        for (int i = 0; i < count; i++) {
            Location location = new Location(provider); location.setLatitude(lat); location.setLongitude(lon);
            location.setAccuracy(30); location.setTime(System.currentTimeMillis());
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            manager.setTestProviderLocation(provider, location); SystemClock.sleep(400);
        }
    }
    private String visible() {
        StringBuilder text = new StringBuilder(); collect(getUiAutomation().getRootInActiveWindow(), text); return text.toString();
    }
    private void collect(AccessibilityNodeInfo node, StringBuilder text) {
        if (node == null) return;
        if (node.getText() != null) text.append(node.getText()).append('\n');
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), text);
    }
    private void invoke(String name) {
        try { Method m = MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private static void set(Object owner, String name, Object value) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner, value); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void await(BooleanSupplier condition, long timeout, String name) {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        check(condition.getAsBoolean(), name);
    }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
    private void pass(String message) { results.add("PASS " + message); }
}
