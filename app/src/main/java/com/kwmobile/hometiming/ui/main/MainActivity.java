package com.kwmobile.hometiming.ui.main;

import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.ImageView;
import android.widget.TextView;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.graphics.Outline;
import android.widget.LinearLayout;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.databinding.ActivityMainBinding;

/** Native design preview. Sample content does not perform account or schedule operations. */
public final class MainActivity extends AppCompatActivity {
    private static final String SELECTED_TAB = "selected_tab";
    private static final String SHEET_STATE = "sheet_state";
    private final SparseArray<View> previewScreens = new SparseArray<>();
    private ActivityMainBinding binding;
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateHomeTime();
        }
    };
    private int homeSheetState = BottomSheetBehavior.STATE_COLLAPSED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureInsets();
        binding.navigationContainer.setClipToOutline(true);
        binding.navigationContainer.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float radius = getResources().getDimension(R.dimen.ht_radius_surface);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        binding.glassBackdrop.setSource(binding.mainContent);
        binding.getRoot().getViewTreeObserver().addOnScrollChangedListener(
                binding.glassBackdrop::refresh);

        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        binding.bottomNavigation.setBackgroundResource(android.R.color.transparent);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            showScreen(item.getItemId());
            return true;
        });
        int selectedTab = savedInstanceState == null
                ? R.id.nav_home : savedInstanceState.getInt(SELECTED_TAB, R.id.nav_home);
        if (savedInstanceState != null) {
            homeSheetState = savedInstanceState.getInt(SHEET_STATE, BottomSheetBehavior.STATE_COLLAPSED);
        }
        binding.bottomNavigation.setSelectedItemId(selectedTab);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                BottomSheetBehavior<View> sheet = currentSheet();
                if (sheet != null && sheet.getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                    sheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
                } else if (binding.bottomNavigation.getSelectedItemId() != R.id.nav_home) {
                    binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void configureInsets() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ht_surface));
        }
        int topPadding = binding.topBar.getPaddingTop();
        int navigationMargin = ((FrameLayout.LayoutParams)
                binding.navigationContainer.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets safeArea = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safeArea.left, 0, safeArea.right, 0);
            binding.topBar.setPadding(binding.topBar.getPaddingLeft(),
                    topPadding + safeArea.top, binding.topBar.getPaddingRight(),
                    binding.topBar.getPaddingBottom());
            FrameLayout.LayoutParams navigationParams = (FrameLayout.LayoutParams)
                    binding.navigationContainer.getLayoutParams();
            navigationParams.bottomMargin = navigationMargin + safeArea.bottom;
            binding.navigationContainer.setLayoutParams(navigationParams);
            FrameLayout.LayoutParams systemBarParams = (FrameLayout.LayoutParams)
                    binding.systemNavigationBackdrop.getLayoutParams();
            systemBarParams.height = safeArea.bottom;
            binding.systemNavigationBackdrop.setLayoutParams(systemBarParams);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void showScreen(int tabId) {
        View screen = previewScreens.get(tabId);
        if (screen == null) {
            int layout = tabId == R.id.nav_items ? R.layout.screen_items
                    : tabId == R.id.nav_settings ? R.layout.screen_settings : R.layout.screen_home;
            screen = getLayoutInflater().inflate(layout, binding.screenContainer, false);
            bindPreviewActions(screen);
            if (tabId == R.id.nav_home) {
                configureSheet(screen);
                BottomSheetBehavior<View> home = BottomSheetBehavior.from(
                        screen.findViewById(R.id.preview_sheet));
                screen.post(() -> home.setState(homeSheetState));
            } else {
                configurePage(screen);
            }
            previewScreens.put(tabId, screen);
        }
        binding.screenContainer.removeAllViews();
        binding.screenContainer.addView(screen);
        updateHomeTime();
        binding.glassBackdrop.refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter clock = new IntentFilter(Intent.ACTION_TIME_TICK);
        clock.addAction(Intent.ACTION_TIME_CHANGED);
        clock.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        ContextCompat.registerReceiver(this, clockReceiver, clock, ContextCompat.RECEIVER_NOT_EXPORTED);
        updateHomeTime();
    }

    @Override
    protected void onStop() {
        unregisterReceiver(clockReceiver);
        super.onStop();
    }

    private void updateHomeTime() {
        ZonedDateTime now = ZonedDateTime.now();
        HomeTimeOfDay period = HomeTimeOfDay.at(now);
        int background;
        int label;
        int icon;
        int iconColor;
        switch (period) {
            case MORNING:
                background = R.color.ht_morning;
                label = R.string.time_morning;
                icon = R.drawable.ic_sun;
                iconColor = R.color.ht_accent;
                break;
            case DAY:
                background = R.color.ht_primary;
                label = R.string.time_day;
                icon = R.drawable.ic_sun;
                iconColor = R.color.ht_accent;
                break;
            case EVENING:
                background = R.color.ht_evening;
                label = R.string.time_evening;
                icon = R.drawable.ic_sunset;
                iconColor = R.color.ht_accent;
                break;
            default:
                background = R.color.ht_night;
                label = R.string.time_night;
                icon = R.drawable.ic_moon;
                iconColor = R.color.ht_moon;
                break;
        }
        boolean homeSelected = binding.screenContainer.findViewById(R.id.preview_sheet) != null;
        int backdropColor = ContextCompat.getColor(this,
                homeSelected ? background : R.color.ht_primary);
        binding.getRoot().setBackgroundColor(backdropColor);
        getWindow().setStatusBarColor(backdropColor);
        View home = previewScreens.get(R.id.nav_home);
        if (home != null) {
            ImageView weatherIcon = home.findViewById(R.id.weather_icon);
            weatherIcon.setImageResource(icon);
            weatherIcon.setImageTintList(ContextCompat.getColorStateList(this, iconColor));
            TextView time = home.findViewById(R.id.weather_time);
            String clock = now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()));
            time.setText(getString(R.string.weather_time, getString(label), clock));
            time.setContentDescription(getString(R.string.weather_time_accessibility,
                    getString(label), clock));
        }
        binding.glassBackdrop.refresh();
    }

    private BottomSheetBehavior<View> currentSheet() {
        View sheet = binding.screenContainer.findViewById(R.id.preview_sheet);
        return sheet == null ? null : BottomSheetBehavior.from(sheet);
    }

    private void configurePage(View screen) {
        screen.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int inset = navigationOverlayInset();
            if (screen.getPaddingBottom() != inset) {
                screen.setPadding(0, 0, 0, inset);
            }
            binding.glassBackdrop.refresh();
        });
    }

    private void configureSheet(View screen) {
        View sheet = screen.findViewById(R.id.preview_sheet);
        View handle = screen.findViewById(R.id.sheet_handle);
        View hero = screen.findViewById(R.id.hero_content);
        View heroScroll = screen.findViewById(R.id.hero_scroll);
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
        handle.setOnClickListener(view -> behavior.setState(
                behavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_COLLAPSED : BottomSheetBehavior.STATE_EXPANDED));
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                syncSheetAccessibility(handle, heroScroll, newState);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                binding.glassBackdrop.refresh();
            }
        });
        ViewCompat.setStateDescription(handle, getString(R.string.sheet_collapsed));
        screen.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int height = bottom - top;
            if (height > 0 && height != oldBottom - oldTop) {
                int glassInset = navigationOverlayInset();
                int minimumPeek = Math.round(224 * getResources().getDisplayMetrics().density)
                        + glassInset;
                int visibleHero = Math.round(96 * getResources().getDisplayMetrics().density);
                int peek = Math.max(minimumPeek, height - hero.getMeasuredHeight());
                View list = screen.findViewById(R.id.preview_scroll);
                list.setPadding(0, 0, 0, glassInset);
                peek = Math.min(peek, Math.max(height / 2, height - visibleHero));
                behavior.setPeekHeight(peek);
                heroScroll.setPadding(0, 0, 0, peek);
            }
            // CoordinatorLayout restores behavior state without dispatching a state callback.
            syncSheetAccessibility(handle, heroScroll, behavior.getState());
            binding.glassBackdrop.refresh();
        });
    }

    private int navigationOverlayInset() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)
                binding.navigationContainer.getLayoutParams();
        int barHeight = binding.navigationContainer.getMeasuredHeight();
        if (barHeight == 0) {
            barHeight = getResources().getDimensionPixelSize(R.dimen.ht_navigation_height)
                    + Math.round(8 * getResources().getDisplayMetrics().density);
        }
        return barHeight + params.bottomMargin
                + getResources().getDimensionPixelSize(R.dimen.ht_space_16);
    }

    private void syncSheetAccessibility(View handle, View heroScroll, int state) {
        if (state != BottomSheetBehavior.STATE_EXPANDED
                && state != BottomSheetBehavior.STATE_COLLAPSED) {
            return;
        }
        boolean expanded = state == BottomSheetBehavior.STATE_EXPANDED;
        handle.setContentDescription(getString(expanded
                ? R.string.sheet_collapse : R.string.sheet_expand));
        ViewCompat.setStateDescription(handle, getString(expanded
                ? R.string.sheet_expanded : R.string.sheet_collapsed));
        heroScroll.setImportantForAccessibility(expanded
                ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }

    private void bindPreviewActions(View screen) {
        int[] headingIds = {R.id.page_title, R.id.section_primary, R.id.section_secondary};
        for (int id : headingIds) {
            View heading = screen.findViewById(id);
            if (heading != null) {
                ViewCompat.setAccessibilityHeading(heading, true);
            }
        }
        int[] groupIds = {R.id.house_group, R.id.notification_group};
        for (int id : groupIds) {
            View group = screen.findViewById(id);
            if (group != null) {
                group.setClipToOutline(true);
            }
        }
        int[] scheduleIds = {R.id.schedule_filter, R.id.schedule_brush, R.id.schedule_bedding};
        int[] scheduleNames = {R.string.item_filter, R.string.item_brush, R.string.item_bedding};
        int[] scheduleDetails = {R.string.item_filter_meta, R.string.item_brush_meta, R.string.item_bedding_meta};
        int[] scheduleStatuses = {R.string.status_today, R.string.status_overdue, R.string.status_tomorrow};
        for (int index = 0; index < scheduleIds.length; index++) {
            int id = scheduleIds[index];
            View row = screen.findViewById(id);
            if (row != null) {
                if (getResources().getConfiguration().fontScale >= 1.5f) {
                    stackScheduleStatus((LinearLayout) row);
                }
                int name = scheduleNames[index];
                String message = getString(R.string.preview_item_notice,
                        getString(scheduleDetails[index]), getString(scheduleStatuses[index]));
                row.setOnClickListener(view -> showPreviewNotice(name, message));
            }
        }
        int[] settingIds = {R.id.settings_house, R.id.settings_family, R.id.settings_notification};
        int[] settingNames = {R.string.settings_house, R.string.settings_family, R.string.settings_notification};
        for (int index = 0; index < settingIds.length; index++) {
            int id = settingIds[index];
            View row = screen.findViewById(id);
            if (row != null) {
                int name = settingNames[index];
                row.setOnClickListener(view -> showPreviewNotice(name,
                        getString(R.string.preview_settings_notice)));
            }
        }
    }

    private void stackScheduleStatus(LinearLayout row) {
        LinearLayout details = (LinearLayout) row.getChildAt(1);
        View status = row.getChildAt(2);
        row.removeView(status);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = getResources().getDimensionPixelSize(R.dimen.ht_space_8);
        details.addView(status, params);
    }

    private void showPreviewNotice(int title, String message) {
        AlertDialog notice = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.preview_dialog_close, null)
                .show();
        if (notice.getWindow() != null) {
            WindowCompat.getInsetsController(notice.getWindow(), notice.getWindow().getDecorView())
                    .setAppearanceLightNavigationBars(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                notice.getWindow().setNavigationBarColor(
                        ContextCompat.getColor(this, R.color.ht_surface));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(SELECTED_TAB, binding.bottomNavigation.getSelectedItemId());
        View home = previewScreens.get(R.id.nav_home);
        if (home != null) {
            BottomSheetBehavior<View> sheet = BottomSheetBehavior.from(
                    home.findViewById(R.id.preview_sheet));
            homeSheetState = sheet.getState() == BottomSheetBehavior.STATE_EXPANDED
                    ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED;
        }
        outState.putInt(SHEET_STATE, homeSheetState);
        super.onSaveInstanceState(outState);
    }
}
