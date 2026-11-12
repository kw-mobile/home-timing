package com.kwmobile.hometiming.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.widget.FrameLayout;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.preview.PreviewStore;
import com.kwmobile.hometiming.data.preview.PreviewStore.Item;
import com.kwmobile.hometiming.databinding.ActivityMainBinding;
import com.kwmobile.hometiming.ui.component.FluidMotion;

import java.time.LocalDate;

/** Complete native design walkthrough. All mutations are restricted to local fixtures. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private DesignPreviewViewModel vm;
    private WeatherViewModel weatherVm;
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

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        vm = new ViewModelProvider(this).get(DesignPreviewViewModel.class);
        vm.restore(saved);
        weatherVm = new ViewModelProvider(this).get(WeatherViewModel.class);
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
        binding.galleryButton.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
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
        if (saved == null) readPreviewIntent(getIntent());
        render();
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
        readPreviewIntent(intent);
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
        vm.edit(id);
        render();
    }

    private void back() {
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
        if (screen != null && screen.findViewById(R.id.auth_email) != null) {
            android.view.autofill.AutofillManager autofill =
                    getSystemService(android.view.autofill.AutofillManager.class);
            if (autofill != null) autofill.cancel();
        }
        String name = s().route.screen;
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
                new HouseholdScreens(screen, vm, host, ui).bindSettings();
                break;
            case "family":
                new HouseholdScreens(screen, vm, host, ui).bindFamily();
                break;
            case "preferences":
                new HouseholdScreens(screen, vm, host, ui).bindPreferences();
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
