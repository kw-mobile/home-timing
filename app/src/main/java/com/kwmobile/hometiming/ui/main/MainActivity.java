package com.kwmobile.hometiming.ui.main;

import android.os.Bundle;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.databinding.ActivityMainBinding;

/** Native design preview. Sample content does not perform account or schedule operations. */
public final class MainActivity extends AppCompatActivity {
    private static final String SELECTED_TAB = "selected_tab";
    private final SparseArray<View> previewScreens = new SparseArray<>();
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        configureInsets();

        binding.bottomNavigation.setItemActiveIndicatorEnabled(false);
        binding.bottomNavigation.setBackgroundResource(R.color.ht_surface);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            showScreen(item.getItemId());
            return true;
        });
        int selectedTab = savedInstanceState == null
                ? R.id.nav_home : savedInstanceState.getInt(SELECTED_TAB, R.id.nav_home);
        binding.bottomNavigation.setSelectedItemId(selectedTab);
    }

    private void configureInsets() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), binding.getRoot());
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.ht_surface));
        } else {
            // API 26 cannot display dark navigation icons on a light background.
            binding.navigationContainer.setBackgroundColor(
                    ContextCompat.getColor(this, R.color.ht_primary));
        }
        int topPadding = binding.topBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets safeArea = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safeArea.left, 0, safeArea.right, 0);
            binding.topBar.setPadding(binding.topBar.getPaddingLeft(),
                    topPadding + safeArea.top, binding.topBar.getPaddingRight(),
                    binding.topBar.getPaddingBottom());
            binding.navigationContainer.setPadding(0, 0, 0, safeArea.bottom);
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
            previewScreens.put(tabId, screen);
        }
        binding.screenContainer.removeAllViews();
        binding.screenContainer.addView(screen);
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.preview_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.preview_dialog_close, null)
                .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(SELECTED_TAB, binding.bottomNavigation.getSelectedItemId());
        super.onSaveInstanceState(outState);
    }
}
