package com.kwmobile.hometiming.ui.main;

import android.os.Bundle;
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
                float radius = 24 * getResources().getDisplayMetrics().density;
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
        binding.bottomNavigation.setSelectedItemId(selectedTab);
        if (savedInstanceState != null) {
            int sheetState = savedInstanceState.getInt(SHEET_STATE, BottomSheetBehavior.STATE_COLLAPSED);
            binding.screenContainer.post(() -> currentSheet().setState(sheetState));
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentSheet().getState() != BottomSheetBehavior.STATE_COLLAPSED) {
                    currentSheet().setState(BottomSheetBehavior.STATE_COLLAPSED);
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
            configureSheet(screen);
            previewScreens.put(tabId, screen);
        }
        binding.screenContainer.removeAllViews();
        binding.screenContainer.addView(screen);
        binding.glassBackdrop.refresh();
    }

    private BottomSheetBehavior<View> currentSheet() {
        return BottomSheetBehavior.from(binding.screenContainer.findViewById(R.id.preview_sheet));
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
        int[] scheduleIds = {R.id.schedule_filter, R.id.schedule_brush, R.id.schedule_bedding};
        for (int id : scheduleIds) {
            View row = screen.findViewById(id);
            if (row != null) {
                if (getResources().getConfiguration().fontScale >= 1.5f) {
                    stackScheduleStatus((LinearLayout) row);
                }
                row.setOnClickListener(view -> showPreviewNotice(R.string.preview_item_notice));
            }
        }
        int[] settingIds = {R.id.settings_house, R.id.settings_family, R.id.settings_notification};
        for (int id : settingIds) {
            View row = screen.findViewById(id);
            if (row != null) {
                row.setOnClickListener(view -> showPreviewNotice(R.string.preview_settings_notice));
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

    private void showPreviewNotice(int message) {
        AlertDialog notice = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.preview_dialog_title)
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
        outState.putInt(SHEET_STATE, currentSheet().getState() == BottomSheetBehavior.STATE_EXPANDED
                ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
        super.onSaveInstanceState(outState);
    }
}
