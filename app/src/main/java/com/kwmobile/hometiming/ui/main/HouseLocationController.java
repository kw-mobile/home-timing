package com.kwmobile.hometiming.ui.main;

import android.Manifest;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.data.weather.Region;
import java.util.List;

/** Activity-scoped region selection: permissions, bounded location/address reads and confirmation. */
final class HouseLocationController {
    private final AppCompatActivity activity;
    private final PreviewHost host;
    private final DesignPreviewViewModel ui;
    private final java.util.function.Supplier<View> currentScreen;
    private static final String HOUSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String HOUSE_PRECISE_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final long HOUSE_LOCATION_TIMEOUT_MS = 20_000;
    private static final long HOUSE_ADDRESS_TIMEOUT_MS = 8_000;
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private Session locationPermissionSession;
    private HouseLocationRequest houseLocationRequest;
    private int houseLocationStatus;
    private com.kwmobile.hometiming.data.weather.CurrentPlaceResolver placeResolver;
    private final androidx.activity.result.ActivityResultLauncher<String[]> locationRequest;

    HouseLocationController(AppCompatActivity activity, PreviewHost host, DesignPreviewViewModel ui,
                            java.util.function.Supplier<View> currentScreen) {
        this.activity = activity;
        this.host = host;
        this.ui = ui;
        this.currentScreen = currentScreen;
        placeResolver = new com.kwmobile.hometiming.data.weather.CurrentPlaceResolver(
                new android.location.Geocoder(activity.getApplicationContext(), java.util.Locale.KOREA));
        locationRequest =
            activity.registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> {
                        Session session = locationPermissionSession;
                        locationPermissionSession = null;
                        if (!canSetHouseLocation(session)) return;
                        if (hasHouseLocationPermission()) readHouseLocation(session);
                        else if (activity.shouldShowRequestPermissionRationale(HOUSE_LOCATION_PERMISSION)) {
                            host.notice(activity.getString(R.string.house_location_denied));
                        } else {
                            new MaterialAlertDialogBuilder(activity)
                                    .setMessage(R.string.house_location_denied)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton(R.string.house_location_settings, (dialog, which) ->
                                            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", activity.getPackageName(), null))))
                                    .show();
                        }
                    });
    }

    boolean active() { return houseLocationRequest != null; }

    private boolean canSetHouseLocation(Session session) {
        MvpViewModel mvp = host.mvp();
        return !activity.isFinishing() && !activity.isDestroyed() && mvp != null
                && session != null && mvp.session == session && mvp.house != null
                && mvp.house.isOwner(session.uid) && !mvp.readOnly() && !mvp.busy()
                && "preferences:region".equals(ui.state.route.key());
    }

    private boolean hasHouseLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, HOUSE_LOCATION_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPreciseHouseLocationPermission() {
        return ContextCompat.checkSelfPermission(activity, HOUSE_PRECISE_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    void request() {
        Session session = host.mvp() == null ? null : host.mvp().session;
        if (!canSetHouseLocation(session) || locationPermissionSession != null || houseLocationRequest != null) return;
        if (hasPreciseHouseLocationPermission()) {
            readHouseLocation(session);
        } else if (hasHouseLocationPermission() || activity.shouldShowRequestPermissionRationale(HOUSE_LOCATION_PERMISSION)) {
            new MaterialAlertDialogBuilder(activity)
                    .setMessage(R.string.house_location_rationale)
                    .setNegativeButton(hasHouseLocationPermission() ? R.string.house_location_approximate : R.string.house_region_manual, (dialog, which) -> {
                        if (hasHouseLocationPermission()) readHouseLocation(session);
                        else chooseRegion();
                    })
                    .setPositiveButton(R.string.house_location_allow, (dialog, which) -> launchLocationPermission(session))
                    .show();
        } else {
            launchLocationPermission(session);
        }
    }

    private void launchLocationPermission(Session session) {
        if (!canSetHouseLocation(session)) return;
        locationPermissionSession = session;
        locationRequest.launch(new String[]{HOUSE_LOCATION_PERMISSION, HOUSE_PRECISE_PERMISSION});
    }

    private void prepareHouseRegions(Session session, Runnable ready) {
        HouseLocationRequest preparing = new HouseLocationRequest(0);
        houseLocationRequest = preparing;
        showHouseLocationProgress(R.string.weather_ui_region_loading);
        locationHandler.postDelayed(() -> {
            if (houseLocationRequest == preparing) {
                cancel(); host.notice(activity.getString(R.string.house_region_load_failed));
            }
        }, HOUSE_ADDRESS_TIMEOUT_MS);
        host.weatherViewModel().prepareRegions().addOnSuccessListener(activity, values -> {
            if (houseLocationRequest != preparing) return;
            cancel();
            if (canSetHouseLocation(session)) ready.run();
        }).addOnFailureListener(activity, ignored -> {
            if (houseLocationRequest != preparing) return;
            cancel();
            if (canSetHouseLocation(session)) host.notice(activity.getString(R.string.house_region_load_failed));
        });
    }

    @android.annotation.SuppressLint("MissingPermission") // Both callbacks and OS calls recheck the grant.
    private void readHouseLocation(Session session) {
        if (!canSetHouseLocation(session)) return;
        if (!hasHouseLocationPermission()) { host.notice(activity.getString(R.string.house_location_denied)); return; }
        if (host.weatherViewModel().regionNames().length == 0) {
            prepareHouseRegions(session, () -> readHouseLocation(session));
            return;
        }
        LocationManager manager = activity.getSystemService(LocationManager.class);
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            host.notice(activity.getString(R.string.house_location_disabled)); return;
        }
        List<String> available = manager.getProviders(true);
        List<String> providers = new java.util.ArrayList<>(3);
        if (available.contains("fused")) providers.add("fused");
        if (available.contains(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
        if (hasPreciseHouseLocationPermission() && available.contains(LocationManager.GPS_PROVIDER))
            providers.add(LocationManager.GPS_PROVIDER);
        if (providers.isEmpty()) { host.notice(activity.getString(R.string.house_location_no_fix)); return; }
        HouseLocationRequest request = new HouseLocationRequest(providers.size());
        houseLocationRequest = request;
        showHouseLocationProgress(R.string.house_location_loading);
        locationHandler.postDelayed(() -> {
            if (houseLocationRequest == request) resolveHouseLocation(session, request);
        }, HOUSE_LOCATION_TIMEOUT_MS);
        for (String provider : providers) {
            CancellationSignal signal = new CancellationSignal(); request.signals.add(signal);
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                        ContextCompat.getMainExecutor(activity), location -> onHouseLocation(session, request, location));
            } catch (SecurityException | IllegalArgumentException failure) { onHouseLocation(session, request, null); }
        }
    }

    private void onHouseLocation(Session session, HouseLocationRequest request, android.location.Location location) {
        if (houseLocationRequest != request || request.resolvingAddress) return;
        if (!canSetHouseLocation(session)) { cancel(); return; }
        if (location != null) {
            long age = android.os.SystemClock.elapsedRealtimeNanos() - location.getElapsedRealtimeNanos();
            if (age >= 0 && age <= 120_000_000_000L
                    && Double.isFinite(location.getLatitude()) && Double.isFinite(location.getLongitude())
                    && location.getLatitude() > -90 && location.getLatitude() < 90
                    && location.getLongitude() >= -180 && location.getLongitude() <= 180
                    && (request.best == null || location.hasAccuracy()
                    && (!request.best.hasAccuracy() || location.getAccuracy() < request.best.getAccuracy()))) {
                request.best = location;
            }
        }
        // Coarse fixes can only suggest districts. With precise access, wait for a useful dong fix.
        if (request.best != null && (!hasPreciseHouseLocationPermission()
                || request.best.hasAccuracy() && request.best.getAccuracy() <= 200)) {
            resolveHouseLocation(session, request); return;
        }
        if (--request.pending == 0) resolveHouseLocation(session, request);
    }

    private void resolveHouseLocation(Session session, HouseLocationRequest request) {
        if (houseLocationRequest != request) return;
        if (!canSetHouseLocation(session) || !hasHouseLocationPermission()) {
            cancel(); return;
        }
        if (request.best == null) {
            cancel(); host.notice(activity.getString(R.string.house_location_no_fix)); return;
        }
        if (!hasPreciseHouseLocationPermission() || !request.best.hasAccuracy() || request.best.getAccuracy() > 200) {
            showApproximateHouseLocation(session, request); return;
        }
        request.resolvingAddress = true;
        locationHandler.removeCallbacksAndMessages(null);
        request.cancelSignals();
        locationHandler.postDelayed(() -> {
            if (houseLocationRequest == request) showApproximateHouseLocation(session, request);
        }, HOUSE_ADDRESS_TIMEOUT_MS);
        showHouseLocationProgress(R.string.house_location_address_loading);
        placeResolver.resolve(request.best.getLatitude(), request.best.getLongitude(), host.weatherViewModel().regions())
                .addOnSuccessListener(activity, region -> {
                    if (houseLocationRequest != request) return;
                    cancel();
                    if (canSetHouseLocation(session) && hasPreciseHouseLocationPermission()) confirmHouseLocation(session, region);
                }).addOnFailureListener(activity, ignored -> {
                    if (houseLocationRequest == request) showApproximateHouseLocation(session, request);
                });
    }

    private void showApproximateHouseLocation(Session session, HouseLocationRequest request) {
        android.location.Location location = request.best;
        cancel();
        if (!canSetHouseLocation(session) || !hasHouseLocationPermission()) return;
        List<Region> candidates = host.weatherViewModel().nearbyRegions(location.getLatitude(), location.getLongitude());
        if (candidates.isEmpty()) host.notice(activity.getString(R.string.house_location_no_region));
        else showHouseLocationCandidates(session, candidates);
    }

    private void showHouseLocationProgress(int message) {
        houseLocationStatus = message;
        bindProgress();
    }

    void bindProgress() {
        View screen = currentScreen.get();
        if (screen == null) return;
        View status = screen.findViewById(R.id.house_location_status);
        if (status == null) return;
        boolean active = active();
        status.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) ((TextView) status).setText(houseLocationStatus);
        screen.findViewById(R.id.house_location_button).setEnabled(!active);
        screen.findViewById(R.id.house_location_cancel).setVisibility(active ? View.VISIBLE : View.GONE);
    }

    void cancel() {
        HouseLocationRequest request = houseLocationRequest; houseLocationRequest = null;
        houseLocationStatus = 0;
        bindProgress();
        locationHandler.removeCallbacksAndMessages(null);
        if (request != null) request.cancelSignals();
    }

    private static final class HouseLocationRequest {
        final List<CancellationSignal> signals = new java.util.ArrayList<>(3);
        int pending;
        android.location.Location best;
        boolean resolvingAddress;
        HouseLocationRequest(int pending) { this.pending = pending; }
        void cancelSignals() { for (CancellationSignal signal : signals) signal.cancel(); }
    }

    private void showHouseLocationCandidates(Session session, List<Region> candidates) {
        String[] names = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) names[i] = candidates.get(i).name;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.house_location_candidates)
                .setItems(names, (dialog, index) -> { if (canSetHouseLocation(session)) confirmHouseLocation(session, candidates.get(index)); })
                .setNeutralButton(R.string.house_region_manual, (dialog, which) -> chooseRegion())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmHouseLocation(Session session, Region region) {
        if (!canSetHouseLocation(session)) return;
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.house_location_check)
                .setMessage(activity.getString(R.string.house_location_confirm, region.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.house_location_save, (dialog, which) -> {
                    if (canSetHouseLocation(session)) host.mvp().region(region);
                }).show();
    }

    void chooseRegion() {
        Session session = host.mvp() == null ? null : host.mvp().session;
        if (!canSetHouseLocation(session)) return;
        cancel(); // Manual selection supersedes any pending GPS/address result.
        List<Region> regions = host.weatherViewModel().regions();
        if (regions.isEmpty()) { prepareHouseRegions(session, this::chooseRegion); return; }
        java.util.Set<String> provinces = new java.util.LinkedHashSet<>();
        for (Region region : regions) provinces.add(region.name.split(" ")[0]);
        String[] provinceNames = provinces.toArray(new String[0]);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.house_region_province)
                .setItems(provinceNames, (dialog, index) -> {
                    if (!canSetHouseLocation(session)) return;
                    List<Region> districts = new java.util.ArrayList<>();
                    for (Region region : regions) if (region.name.split(" ")[0].equals(provinceNames[index])) districts.add(region);
                    String[] names = new String[districts.size()];
                    for (int i = 0; i < names.length; i++) names[i] = districts.get(i).name;
                    new MaterialAlertDialogBuilder(activity).setTitle(R.string.house_region_district)
                            .setItems(names, (pick, selected) -> { if (canSetHouseLocation(session)) confirmHouseLocation(session, districts.get(selected)); })
                            .setNegativeButton(android.R.string.cancel, null).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

}
