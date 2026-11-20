package com.kwmobile.hometiming.ui.main;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.widget.FrameLayout;
import android.graphics.Outline;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.databinding.ActivityMainBinding;
import com.kwmobile.hometiming.ui.component.FluidMotion;

import com.kwmobile.hometiming.data.auth.Session;
import com.kwmobile.hometiming.data.weather.Region;

import java.time.LocalDate;
import java.util.List;

/** Complete native design walkthrough. All mutations are restricted to local fixtures. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private DesignPreviewViewModel vm;
    private WeatherViewModel weatherVm;
    private com.kwmobile.hometiming.data.update.UpdateManager updates;
    private MvpViewModel mvp;
    private boolean fixtures;
    private WeatherViewModel.State shownWeatherState;
    private View screen;
    private boolean syncingNavigation;
    private final BroadcastReceiver clock =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    weatherVm.refresh(false);
                    if (s().route.screen.equals("weather")) { capture(); render(); }
                    else if (overview != null) overview.updateWeather();
                }
            };
    private OverviewScreens overview;
    private final PreviewHost host =
            new PreviewHost() {
                public MvpViewModel mvp() { return fixtures ? null : mvp; }
                public void notificationPermission() {
                    if (android.os.Build.VERSION.SDK_INT >= 33) notificationRequest.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                    else notificationSettings();
                }
                public void photoName() { startPhotoName(); }
                public void useCurrentHouseLocation() { requestHouseLocation(); }
                public void chooseHouseRegion() { MainActivity.this.chooseHouseRegion(); }

                public void notificationSettings() {
                    startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
                public void exactAlarmSettings() {
                    Intent intent = com.kwmobile.hometiming.notify.ReminderManager.getInstance(MainActivity.this).exactAlarmSettingsIntent();
                    if (intent != null) startActivity(intent);
                }
                public WeatherViewModel weatherViewModel() { return weatherVm; }

                public android.widget.ImageView homeWeatherBackdrop() { return binding.homeWeatherBackdrop; }

                public void prepareHomeBackdrop(View hero) { MainActivity.this.prepareHomeBackdrop(hero); }

                public void go(String name, String id) {
                    MainActivity.this.go(name, id);
                }

                public void edit(String id) {
                    MainActivity.this.edit(id);
                }

                public void render() {
                    MainActivity.this.render();
                }

                public void capture() {
                    MainActivity.this.capture();
                }

                public void hideKeyboard() {
                    MainActivity.this.hideKeyboard();
                }

                public void notice(String message) {
                    MainActivity.this.notice(message);
                }

                public void recordCompletion(Item item, LocalDate date) {
                    MainActivity.this.recordCompletion(item, date);
                }

                public boolean isCurrent(View root, String key) {
                    return !isFinishing()
                            && !isDestroyed()
                            && screen == root
                            && s().route.key().equals(key);
                }

                public void refreshGlass() {
                    binding.glassBackdrop.refresh();
                }
            };

    private final androidx.activity.result.ActivityResultLauncher<String> notificationRequest =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    granted -> { if (screen != null) { capture(); render(); } });

    private static final String HOUSE_LOCATION_PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String HOUSE_PRECISE_PERMISSION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final long HOUSE_LOCATION_TIMEOUT_MS = 20_000;
    private static final long HOUSE_ADDRESS_TIMEOUT_MS = 8_000;
    private final Handler locationHandler = new Handler(Looper.getMainLooper());
    private Session locationPermissionSession;
    private HouseLocationRequest houseLocationRequest;
    private com.kwmobile.hometiming.data.weather.CurrentPlaceResolver placeResolver;
    private final androidx.activity.result.ActivityResultLauncher<String[]> locationRequest =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                    granted -> {
                        Session session = locationPermissionSession;
                        locationPermissionSession = null;
                        if (!canSetHouseLocation(session)) return;
                        if (hasHouseLocationPermission()) readHouseLocation(session);
                        else if (shouldShowRequestPermissionRationale(HOUSE_LOCATION_PERMISSION)) {
                            notice(getString(R.string.house_location_denied));
                        } else {
                            new MaterialAlertDialogBuilder(this)
                                    .setMessage(R.string.house_location_denied)
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .setPositiveButton(R.string.house_location_settings, (dialog, which) ->
                                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                    Uri.fromParts("package", getPackageName(), null))))
                                    .show();
                        }
                    });

    private boolean canSetHouseLocation(Session session) {
        return !isFinishing() && !isDestroyed() && vm != null && mvp != null
                && session != null && mvp.session == session && mvp.house != null
                && mvp.house.isOwner(session.uid) && !mvp.readOnly() && !mvp.busy()
                && "preferences:region".equals(s().route.key());
    }

    private boolean hasHouseLocationPermission() {
        return ContextCompat.checkSelfPermission(this, HOUSE_LOCATION_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasPreciseHouseLocationPermission() {
        return ContextCompat.checkSelfPermission(this, HOUSE_PRECISE_PERMISSION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void requestHouseLocation() {
        Session session = mvp == null ? null : mvp.session;
        if (!canSetHouseLocation(session) || locationPermissionSession != null || houseLocationRequest != null) return;
        if (hasPreciseHouseLocationPermission()) {
            readHouseLocation(session);
        } else if (hasHouseLocationPermission() || shouldShowRequestPermissionRationale(HOUSE_LOCATION_PERMISSION)) {
            new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.house_location_rationale)
                    .setNegativeButton(hasHouseLocationPermission() ? R.string.house_location_approximate : R.string.house_region_manual, (dialog, which) -> {
                        if (hasHouseLocationPermission()) readHouseLocation(session);
                        else chooseHouseRegion();
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
        notice(getString(R.string.weather_ui_region_loading));
        locationHandler.postDelayed(() -> {
            if (houseLocationRequest == preparing) {
                cancelHouseLocation(); notice(getString(R.string.house_region_load_failed));
            }
        }, HOUSE_ADDRESS_TIMEOUT_MS);
        weatherVm.prepareRegions().addOnSuccessListener(this, values -> {
            if (houseLocationRequest != preparing) return;
            cancelHouseLocation();
            if (canSetHouseLocation(session)) ready.run();
        }).addOnFailureListener(this, ignored -> {
            if (houseLocationRequest != preparing) return;
            cancelHouseLocation();
            if (canSetHouseLocation(session)) notice(getString(R.string.house_region_load_failed));
        });
    }

    @android.annotation.SuppressLint("MissingPermission") // Both callbacks and OS calls recheck the grant.
    private void readHouseLocation(Session session) {
        if (!canSetHouseLocation(session)) return;
        if (!hasHouseLocationPermission()) { notice(getString(R.string.house_location_denied)); return; }
        if (weatherVm.regionNames().length == 0) {
            prepareHouseRegions(session, () -> readHouseLocation(session));
            return;
        }
        LocationManager manager = getSystemService(LocationManager.class);
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            notice(getString(R.string.house_location_disabled)); return;
        }
        List<String> available = manager.getProviders(true);
        List<String> providers = new java.util.ArrayList<>(3);
        if (available.contains("fused")) providers.add("fused");
        if (available.contains(LocationManager.NETWORK_PROVIDER)) providers.add(LocationManager.NETWORK_PROVIDER);
        if (hasPreciseHouseLocationPermission() && available.contains(LocationManager.GPS_PROVIDER))
            providers.add(LocationManager.GPS_PROVIDER);
        if (providers.isEmpty()) { notice(getString(R.string.house_location_no_fix)); return; }
        HouseLocationRequest request = new HouseLocationRequest(providers.size());
        houseLocationRequest = request;
        notice(getString(R.string.house_location_loading));
        locationHandler.postDelayed(() -> {
            if (houseLocationRequest == request) resolveHouseLocation(session, request);
        }, HOUSE_LOCATION_TIMEOUT_MS);
        for (String provider : providers) {
            CancellationSignal signal = new CancellationSignal(); request.signals.add(signal);
            try {
                LocationManagerCompat.getCurrentLocation(manager, provider, signal,
                        ContextCompat.getMainExecutor(this), location -> onHouseLocation(session, request, location));
            } catch (SecurityException | IllegalArgumentException failure) { onHouseLocation(session, request, null); }
        }
    }

    private void onHouseLocation(Session session, HouseLocationRequest request, android.location.Location location) {
        if (houseLocationRequest != request || request.resolvingAddress) return;
        if (!canSetHouseLocation(session)) { cancelHouseLocation(); return; }
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
            cancelHouseLocation(); return;
        }
        if (request.best == null) {
            cancelHouseLocation(); notice(getString(R.string.house_location_no_fix)); return;
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
        notice(getString(R.string.house_location_address_loading));
        placeResolver.resolve(request.best.getLatitude(), request.best.getLongitude(), weatherVm.regions())
                .addOnSuccessListener(this, region -> {
                    if (houseLocationRequest != request) return;
                    cancelHouseLocation();
                    if (canSetHouseLocation(session) && hasPreciseHouseLocationPermission()) confirmHouseLocation(session, region);
                }).addOnFailureListener(this, ignored -> {
                    if (houseLocationRequest == request) showApproximateHouseLocation(session, request);
                });
    }

    private void showApproximateHouseLocation(Session session, HouseLocationRequest request) {
        android.location.Location location = request.best;
        cancelHouseLocation();
        if (!canSetHouseLocation(session) || !hasHouseLocationPermission()) return;
        List<Region> candidates = weatherVm.nearbyRegions(location.getLatitude(), location.getLongitude());
        if (candidates.isEmpty()) notice(getString(R.string.house_location_no_region));
        else showHouseLocationCandidates(session, candidates);
    }

    private void cancelHouseLocation() {
        HouseLocationRequest request = houseLocationRequest; houseLocationRequest = null;
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
        new MaterialAlertDialogBuilder(this).setTitle(R.string.house_location_candidates)
                .setItems(names, (dialog, index) -> { if (canSetHouseLocation(session)) confirmHouseLocation(session, candidates.get(index)); })
                .setNeutralButton(R.string.house_region_manual, (dialog, which) -> chooseHouseRegion())
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmHouseLocation(Session session, Region region) {
        if (!canSetHouseLocation(session)) return;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.house_location_check)
                .setMessage(getString(R.string.house_location_confirm, region.name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.house_location_save, (dialog, which) -> {
                    if (canSetHouseLocation(session)) mvp.region(region);
                }).show();
    }

    private void chooseHouseRegion() {
        Session session = mvp == null ? null : mvp.session;
        if (!canSetHouseLocation(session)) return;
        cancelHouseLocation(); // Manual selection supersedes any pending GPS/address result.
        List<Region> regions = weatherVm.regions();
        if (regions.isEmpty()) { prepareHouseRegions(session, this::chooseHouseRegion); return; }
        java.util.Set<String> provinces = new java.util.LinkedHashSet<>();
        for (Region region : regions) provinces.add(region.name.split(" ")[0]);
        String[] provinceNames = provinces.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.house_region_province)
                .setItems(provinceNames, (dialog, index) -> {
                    if (!canSetHouseLocation(session)) return;
                    List<Region> districts = new java.util.ArrayList<>();
                    for (Region region : regions) if (region.name.split(" ")[0].equals(provinceNames[index])) districts.add(region);
                    String[] names = new String[districts.size()];
                    for (int i = 0; i < names.length; i++) names[i] = districts.get(i).name;
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.house_region_district)
                            .setItems(names, (pick, selected) -> { if (canSetHouseLocation(session)) confirmHouseLocation(session, districts.get(selected)); })
                            .setNegativeButton(android.R.string.cancel, null).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> photoRequest =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (vm == null) return;
                Intent data = result.getData();
                boolean same = vm.photoDraft != null && vm.photoDraft == s().draft && !s().draft.chore
                        && s().route.screen.equals("editor") && data != null
                        && java.util.Objects.equals(vm.photoRequest, data.getStringExtra(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.REQUEST));
                if (mvp != null) same &= com.kwmobile.hometiming.data.auth.OperationScope.getInstance()
                        .isCurrent(vm.photoScope, com.kwmobile.hometiming.data.firestore.ReminderRepository.getInstance().currentUid());
                vm.photoDraft = null; vm.photoRequest = null; vm.photoScope = null;
                if (result.getResultCode() != RESULT_OK) return;
                if (!same) { notice(getString(R.string.ocr_outdated)); return; }
                String name = com.kwmobile.hometiming.domain.ProductNameCandidates.normalize(
                        data.getStringExtra(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.NAME));
                if (name.isEmpty() || name.length() > 60) return;
                s().draft.name = name; s().draft.dirty = true; render();
            });

    private void startPhotoName() {
        if (s().draft == null || s().draft.chore) return;
        capture(); hideKeyboard();
        vm.photoDraft = s().draft; vm.photoRequest = java.util.UUID.randomUUID().toString();
        vm.photoScope = mvp == null ? null : com.kwmobile.hometiming.data.auth.OperationScope.getInstance()
                .capture(com.kwmobile.hometiming.data.firestore.ReminderRepository.getInstance().currentUid());
        if (mvp != null && vm.photoScope == null) { vm.photoDraft = null; return; }
        photoRequest.launch(new Intent(this, com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.class)
                .putExtra(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.REQUEST, vm.photoRequest)
                .putExtra(com.kwmobile.hometiming.ui.recognition.PhotoNameActivity.OLD_NAME, s().draft.name));
    }

    private DesignPreviewViewModel.State s() {
        return vm.state;
    }

    private int color(int id) {
        return ContextCompat.getColor(this, id);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean rootPage() {
        return isTab(s().route.screen);
    }

    private static boolean isTab(String name) {
        return name.equals("home")
                || name.equals("items")
                || name.equals("calendar")
                || name.equals("settings");
    }

    private void offerAppUpdate(com.kwmobile.hometiming.data.update.UpdateManager.State state) {
        if (state == null || state.info == null || isFinishing() || vm == null || !updates.shouldPrompt()
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                || !rootPage() || fixtures || mvp == null || mvp.session == null) return;
        updates.markPrompted();
        new MaterialAlertDialogBuilder(this).setTitle(R.string.update_title)
                .setMessage(getString(R.string.update_available, state.info.versionName, (state.info.sizeBytes + 1048575) / 1048576))
                .setNegativeButton(R.string.update_later, null)
                .setPositiveButton(state.stage == com.kwmobile.hometiming.data.update.UpdateManager.Stage.READY
                        ? R.string.update_install : R.string.update_download, (dialog, which) -> startActivity(
                                new Intent(this, com.kwmobile.hometiming.ui.update.UpdateActivity.class)
                                        .putExtra(com.kwmobile.hometiming.ui.update.UpdateActivity.AUTO_START, true))).show();
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(DesignPreviewViewModel.class);
        vm.restore(saved);
        updates = com.kwmobile.hometiming.data.update.UpdateManager.get(this);
        updates.changes().observe(this, this::offerAppUpdate);
        weatherVm = new ViewModelProvider(this).get(WeatherViewModel.class);
        placeResolver = new com.kwmobile.hometiming.data.weather.CurrentPlaceResolver(
                new android.location.Geocoder(getApplicationContext(), java.util.Locale.KOREA));
        s().region = weatherVm.regionName();
        configureInsets();
        binding.mainContent.setBackgroundColor(color(R.color.ht_background));
        binding.systemNavigationBackdrop.getLayoutParams().height = dp(16);
        binding.navigationContainer.setClipToOutline(true);
        binding.navigationContainer.setOutlineProvider(
                new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(
                                0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
                    }
                });
        binding.glassBackdrop.setSource(binding.mainContent);
        binding.getRoot()
                .getViewTreeObserver()
                .addOnScrollChangedListener(binding.glassBackdrop::refresh);
        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        binding.bottomNavigation.setBackgroundResource(android.R.color.transparent);
        binding.bottomNavigation.setOnItemSelectedListener(
                item -> {
                    if (syncingNavigation) return true;
                    capture();
                    vm.tab(
                            item.getItemId() == R.id.nav_items
                                    ? "items"
                                    : item.getItemId() == R.id.nav_calendar
                                            ? "calendar"
                                            : item.getItemId() == R.id.nav_settings
                                                    ? "settings"
                                                    : "home");
                    render();
                    return true;
                });
        binding.backButton.setOnClickListener(v -> back());
        binding.galleryButton.setVisibility(BuildConfig.DEBUG && getIntent().hasExtra("preview_screen") ? View.VISIBLE : View.GONE);
        binding.galleryButton.setOnClickListener(v -> go("gallery"));
        getOnBackPressedDispatcher()
                .addCallback(
                        this,
                        new OnBackPressedCallback(true) {
                            @Override
                            public void handleOnBackPressed() {
                                back();
                            }
                        });
        fixtures = BuildConfig.DEBUG && getIntent().hasExtra("preview_screen");
        if (fixtures) {
            s().live = false;
            if (saved == null) readPreviewIntent(getIntent());
        } else {
            mvp = new ViewModelProvider(this).get(MvpViewModel.class);
            mvp.attach(vm);
            if (saved == null) openReminder(getIntent());
            weatherVm.useSharedRegion(null);
        }
        render();
        if (!fixtures) mvp.changes().observe(this, ignored -> {
            if (screen == null) return;
            capture();
            weatherVm.useSharedRegion(mvp.house);
            if (s().route.screen.equals("editor") && screen.findViewById(R.id.edit_submit) != null) {
                MaterialButton submit = screen.findViewById(R.id.edit_submit);
                submit.setEnabled(!mvp.busy() && !mvp.readOnly());
                submit.setText(mvp.busy() ? R.string.mvp_saving :
                        s().draft != null && s().draft.id.isEmpty() ? R.string.preview_copy_097 : R.string.preview_copy_098);
            } else render();
            String message = mvp.takeMessage();
            if (message != null) notice(message);
        });
        shownWeatherState=weatherVm.current();
        weatherVm.state().observe(this,state -> {
            if(state==shownWeatherState)return;
            shownWeatherState=state;
            s().region=weatherVm.regionName();
            if(screen==null)return;
            String page=s().route.screen;
            if(page.equals("home")||page.equals("weather")||page.equals("settings")
                    ||page.equals("preferences")&&(s().route.id.equals("weather")||s().route.id.equals("region"))) {
                capture();render();
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        capture();
        if (fixtures) readPreviewIntent(intent);
        else openReminder(intent);
        render();
    }

    private void readPreviewIntent(Intent intent) {
        if (!BuildConfig.DEBUG) return;
        String route = intent.getStringExtra("preview_screen");
        if (route != null
                && java.util.Arrays.asList(
                                "home",
                                "items",
                                "calendar",
                                "settings",
                                "login",
                                "signup",
                                "reset",
                                "editor",
                                "detail",
                                "weather",
                                "family",
                                "preferences",
                                "gallery",
                                "components")
                        .contains(route)) {
            s().back.clear();
            s().route =
                    new DesignPreviewViewModel.Route(
                            route,
                            intent.getStringExtra("preview_id") == null
                                    ? ""
                                    : intent.getStringExtra("preview_id"));
            if (route.equals("editor")) {
                vm.edit(s().route.id);
                s().back.clear();
            }
            if (route.equals("detail") && s().route.id.isEmpty())
                s().route = new DesignPreviewViewModel.Route("detail", "filter");
        }
        String scenario = intent.getStringExtra("preview_state");
        if (scenario != null
                && java.util.Arrays.asList("normal", "empty", "error", "loading", "offline")
                        .contains(scenario)) s().scenario = scenario;
        String weather = intent.getStringExtra("preview_weather");
        if (weather != null
                && java.util.Arrays.asList("auto", "morning", "day", "evening", "night")
                        .contains(weather)) s().weather = weather;
        String condition = intent.getStringExtra("preview_condition");
        if (condition != null
                && java.util.Arrays.asList("clear", "clouds", "rain").contains(condition))
            s().condition = condition;
    }

    private void openReminder(Intent intent) {
        if (mvp == null) return;
        mvp.openReminder(intent.getStringExtra(com.kwmobile.hometiming.notify.ReminderManager.EXTRA_KIND),
                intent.getStringExtra(com.kwmobile.hometiming.notify.ReminderManager.EXTRA_DOCUMENT_ID));
    }

    @Override protected void onResume() {
        super.onResume();
        if (mvp != null && screen != null) { capture(); render(); }
        if (updates != null) { updates.onForeground(); offerAppUpdate(updates.current()); }
    }

    private void configureInsets() {
        boolean light =
                (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        controller.setAppearanceLightStatusBars(light);
        controller.setAppearanceLightNavigationBars(light);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(color(R.color.ht_background));
        ViewCompat.setOnApplyWindowInsetsListener(
                binding.getRoot(),
                (view, insets) -> {
                    Insets safe =
                            insets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout());
                    int ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                    view.setPadding(safe.left, safe.top, safe.right, Math.max(safe.bottom, ime));
                    return WindowInsetsCompat.CONSUMED;
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this, clock, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        if (overview != null) overview.updateWeather();
    }

    @Override
    protected void onStop() {
        cancelHouseLocation();
        capture();
        unregisterReceiver(clock);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        capture();
        vm.save(out);
        super.onSaveInstanceState(out);
    }

    private void capture() {
        if (screen == null) return;
        NestedScrollView scroll = screen.findViewById(R.id.preview_scroll);
        if (scroll != null) s().scrolls.put(s().route.key(), scroll.getScrollY());
    }

    private void go(String name) {
        go(name, "");
    }

    private void go(String name, String id) {
        capture();
        hideKeyboard();
        vm.navigate(name, id);
        render();
    }

    private void edit(String id) {
        capture();
        if (mvp != null && !fixtures) mvp.beginEdit(id);
        vm.edit(id);
        render();
    }

    private void back() {
        if (!fixtures && mvp != null && mvp.session == null && s().route.screen.equals("login")) {
            finish(); return;
        }
        if (s().route.screen.equals("editor") && s().draft != null && s().draft.dirty) {
            new PreviewWidgets(screen)
                    .confirm(
                            getString(R.string.preview_copy_001),
                            getString(R.string.preview_copy_002),
                            getString(R.string.preview_copy_003),
                            () -> {
                                s().draft = null;
                                finishBack();
                            });
            return;
        }
        if (s().route.screen.equals("home")) {
            View sheet = screen.findViewById(R.id.preview_sheet);
            if (sheet != null
                    && BottomSheetBehavior.from(sheet).getState()
                            != BottomSheetBehavior.STATE_HIDDEN) {
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_HIDDEN);
                return;
            }
        }
        finishBack();
    }

    private void finishBack() {
        capture();
        hideKeyboard();
        if (vm.back()) render();
        else finish();
    }

    private void hideKeyboard() {
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
    }

    private void render() {
        // These are simulated auth results. Never offer to save demo credentials to the OS.
        if (fixtures && screen != null && screen.findViewById(R.id.auth_email) != null) {
            android.view.autofill.AutofillManager autofill =
                    getSystemService(android.view.autofill.AutofillManager.class);
            if (autofill != null) autofill.cancel();
        }
        if (!fixtures && mvp != null && mvp.session == null
                && !java.util.Arrays.asList("login", "signup", "reset").contains(s().route.screen)) vm.tab("login");
        String name = s().route.screen;
        if (!fixtures && mvp != null) mvp.watchHistory(name.equals("detail") ? s().route.id : "");
        int pageColor = color(R.color.ht_background);
        boolean home = name.equals("home");
        binding.mainContent.setBackgroundColor(home ? android.graphics.Color.TRANSPARENT : pageColor);
        binding.topBar.setBackgroundColor(home ? android.graphics.Color.TRANSPARENT : pageColor);
        binding.homeWeatherBackdrop.setVisibility(home ? View.VISIBLE : View.GONE);
        int toolbarColor = color(home ? R.color.ht_photo_text : R.color.ht_text);
        binding.toolbarTitle.setTextColor(toolbarColor);
        binding.brandIcon.setImageTintList(ColorStateList.valueOf(toolbarColor));
        binding.galleryButton.setTextColor(toolbarColor);
        boolean light = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(!home && light);
        screen = getLayoutInflater().inflate(layoutFor(name), binding.screenContainer, false);
        binding.screenContainer.removeAllViews();
        binding.screenContainer.addView(screen);
        updateNavigation(name);
        restoreScroll();
        bindScreen(name, new PreviewWidgets(screen));
        motion(screen);
        if (!home) FluidMotion.enter(screen, binding.glassBackdrop::refresh);
        binding.glassBackdrop.refresh();
        ViewCompat.setAccessibilityPaneTitle(screen, toolbarTitle(name));
        if (updates != null) offerAppUpdate(updates.current());
    }

    private void prepareHomeBackdrop(View homePhoto) {
        // Extend only the background; preserve the hero, text and sheet geometry.
        homePhoto.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        screen.findViewById(R.id.weather_scrim).setVisibility(View.INVISIBLE);
        screen.findViewById(R.id.weather_photo).setVisibility(View.INVISIBLE);
        View homeScreen=screen;
        Runnable sizeBackdrop=() -> {
            if(screen!=homeScreen)return;
            int[] heroPosition=new int[2],rootPosition=new int[2];
            homePhoto.getLocationInWindow(heroPosition);binding.getRoot().getLocationInWindow(rootPosition);
            FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)binding.homeWeatherBackdrop.getLayoutParams();
            int height=Math.max(0,heroPosition[1]+homePhoto.getHeight()-rootPosition[1]);
            int top=-binding.getRoot().getPaddingTop();
            if(size.height!=height||size.topMargin!=top) {
                size.height=height;size.topMargin=top;binding.homeWeatherBackdrop.setLayoutParams(size);
            }
        };
        homePhoto.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> sizeBackdrop.run());
        binding.getRoot().addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob) {
                if(screen!=homeScreen){v.removeOnLayoutChangeListener(this);return;}
                sizeBackdrop.run();
            }
        });
        homePhoto.post(sizeBackdrop);
        ((NestedScrollView)screen.findViewById(R.id.hero_scroll)).setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)(v,x,y,oldX,oldY) -> sizeBackdrop.run());
    }

    private int layoutFor(String name) {
        switch (name) {
            case "login":
                return R.layout.screen_login;
            case "signup":
                return R.layout.screen_signup;
            case "reset":
                return R.layout.screen_reset;
            case "items":
                return R.layout.screen_items;
            case "calendar":
                return R.layout.screen_calendar;
            case "editor":
                return R.layout.screen_editor;
            case "detail":
                return R.layout.screen_detail;
            case "weather":
                return R.layout.screen_weather;
            case "settings":
                return R.layout.screen_settings;
            case "family":
                return R.layout.screen_family;
            case "preferences":
                return R.layout.screen_preferences;
            case "components":
            case "gallery":
                return R.layout.screen_gallery;
            default:
                return R.layout.screen_home;
        }
    }

    private void updateNavigation(String name) {
        boolean root = rootPage();
        binding.topBar.setVisibility(!fixtures && name.equals("login") ? View.GONE : View.VISIBLE);
        binding.backButton.setVisibility(root ? View.GONE : View.VISIBLE);
        binding.brandIcon.setVisibility(root ? View.VISIBLE : View.GONE);
        binding.toolbarTitle.setText(
                root ? getString(R.string.preview_copy_004) : toolbarTitle(name));
        binding.navigationContainer.setVisibility(root ? View.VISIBLE : View.GONE);
        binding.systemNavigationBackdrop.setVisibility(root ? View.VISIBLE : View.GONE);
        if (root) {
            syncingNavigation = true;
            binding.bottomNavigation.setSelectedItemId(
                    name.equals("items")
                            ? R.id.nav_items
                            : name.equals("calendar")
                                    ? R.id.nav_calendar
                                    : name.equals("settings") ? R.id.nav_settings : R.id.nav_home);
            syncingNavigation = false;
        }
    }

    private void restoreScroll() {
        NestedScrollView scroll = screen.findViewById(R.id.preview_scroll);
        if (scroll != null) {
            scroll.setPadding(0, 0, 0, rootPage() ? dp(100) : dp(24));
            int y = s().scrolls.getOrDefault(s().route.key(), 0);
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    private void bindScreen(String name, PreviewWidgets ui) {
        overview = null;
        switch (name) {
            case "login":
            case "signup":
            case "reset":
                new AccountScreens(screen, vm, host, ui).bindAuth(name);
                break;
            case "editor":
                new ItemScreens(screen, vm, host, ui).bindEditor();
                break;
            case "detail":
                new ItemScreens(screen, vm, host, ui).bindDetail();
                break;
            case "settings":
                if (fixtures) new HouseholdScreens(screen, vm, host, ui).bindSettings();
                else new MvpHouseholdScreens(screen, host, ui).settings();
                break;
            case "family":
                if (fixtures) new HouseholdScreens(screen, vm, host, ui).bindFamily();
                else new MvpHouseholdScreens(screen, host, ui).family();
                break;
            case "preferences":
                if (fixtures) new HouseholdScreens(screen, vm, host, ui).bindPreferences();
                else new MvpHouseholdScreens(screen, host, ui).preferences(s().route.id);
                break;
            case "components":
                new CatalogScreens(screen, vm, host, ui).bindComponents();
                break;
            case "gallery":
                new CatalogScreens(screen, vm, host, ui).bindGallery();
                break;
            default:
                overview = new OverviewScreens(screen, vm, host, ui);
                switch (name) {
                    case "items":
                        overview.bindItems();
                        break;
                    case "calendar":
                        overview.bindCalendar();
                        break;
                    case "weather":
                        overview.bindWeather();
                        break;
                    default:
                        overview.bindHome();
                        break;
                }
        }
    }

    private String toolbarTitle(String name) {
        switch (name) {
            case "login":
                return getString(R.string.preview_copy_005);
            case "signup":
                return getString(R.string.preview_copy_006);
            case "reset":
                return getString(R.string.preview_copy_007);
            case "editor":
                return s().route.id.isEmpty()
                        ? getString(R.string.preview_copy_008)
                        : getString(R.string.preview_copy_009);
            case "detail":
                return getString(R.string.preview_copy_010);
            case "weather":
                return getString(R.string.preview_copy_011);
            case "family":
                return getString(R.string.preview_copy_012);
            case "preferences":
                return getString(R.string.preview_copy_013);
            case "components":
                return getString(R.string.component_catalog);
            case "gallery":
                return getString(R.string.preview_copy_014);
            case "items":
                return getString(R.string.preview_copy_015);
            case "calendar":
                return getString(R.string.preview_copy_016);
            case "settings":
                return getString(R.string.preview_copy_013);
            default:
                return getString(R.string.preview_copy_017);
        }
    }

    private void motion(View view) {
        if (view instanceof MaterialButton) FluidMotion.press(view);
        if (view instanceof ViewGroup)
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
                motion(((ViewGroup) view).getChildAt(i));
    }

    private void recordCompletion(Item item, LocalDate actualDate) {
        if (!fixtures) { mvp.complete(item, actualDate); return; }
        if (s().scenario.equals("offline")) {
            notice(getString(R.string.preview_copy_130));
            return;
        }
        if (s().scenario.equals("error")) {
            notice(getString(R.string.preview_copy_135));
            return;
        }
        PreviewStore.CompletionReceipt receipt = s().store.complete(item.id, actualDate);
        if (receipt == null) {
            notice(
                    getString(
                            s().store.completedToday(item)
                                    ? R.string.preview_copy_137
                                    : R.string.component_completion_invalid));
            return;
        }
        s().completionDates.remove(item.id);
        capture();
        render();
        PreviewWidgets ui = new PreviewWidgets(screen);
        Snackbar.make(
                        binding.getRoot(),
                        getString(
                                R.string.component_completion_saved,
                                item.name,
                                ui.date(actualDate)),
                        10000)
                .setAnchorView(rootPage() ? binding.navigationContainer : null)
                .setAction(
                        R.string.component_undo,
                        v -> {
                            boolean restored = s().store.undo(receipt);
                            capture();
                            render();
                            notice(
                                    getString(
                                            restored
                                                    ? R.string.component_undone
                                                    : R.string.component_undo_unavailable));
                        })
                .show();
    }

    private void notice(String message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG)
                .setAnchorView(rootPage() ? binding.navigationContainer : null)
                .show();
    }
}
