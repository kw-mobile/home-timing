package com.kwmobile.hometiming.ui.calendar;

import android.Manifest;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;

public final class CalendarSettingsActivity extends AppCompatActivity {
    private CalendarSettingsViewModel vm;
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> vm.refresh());
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_calendar_settings);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.feature_heading), true);
        boolean light = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.calendar_settings_root)).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.calendar_settings_root)).setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.calendar_settings_root), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        vm = new ViewModelProvider(this).get(CalendarSettingsViewModel.class);
        findViewById(R.id.calendar_back).setOnClickListener(v -> finish());
        findViewById(R.id.calendar_details).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.calendar_title).setMessage(R.string.calendar_help)
                .setPositiveButton(android.R.string.ok, null).show());
        findViewById(R.id.calendar_choose).setOnClickListener(v -> {
            if (!vm.manager.permitted()) { permissions.launch(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}); return; }
            if (vm.choices.isEmpty()) { vm.refresh(); return; }
            new MaterialAlertDialogBuilder(this).setTitle(R.string.calendar_choose)
                    .setItems(vm.choices.stream().map(c -> c.label).toArray(String[]::new), (dialog, which) -> vm.enable(which))
                    .setNegativeButton(android.R.string.cancel, null).show();
        });
        findViewById(R.id.calendar_sync).setOnClickListener(v -> vm.sync());
        findViewById(R.id.calendar_disable).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.calendar_disable).setMessage(R.string.calendar_disable_help)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.calendar_disable, (d, w) -> vm.disable()).show());
        vm.changes().observe(this, ignored -> render());
    }
    @Override protected void onResume() { super.onResume(); if (vm != null) { vm.refresh(); vm.manager.sync(); } }
    private void render() {
        String status = getString(!vm.manager.permitted() ? R.string.calendar_permission :
                vm.manager.enabled() ? R.string.calendar_enabled : R.string.calendar_off);
        if (vm.manager.enabled()) status += "\n" + vm.manager.label();
        if (vm.manager.cleanupPending()) status += "\n" + getString(R.string.calendar_cleanup_pending);
        if (vm.manager.permitted() && vm.choices.isEmpty() && !vm.busy) status += "\n" + getString(R.string.calendar_empty);
        if (vm.message != 0) status += "\n" + getString(vm.message);
        ((TextView) findViewById(R.id.calendar_status)).setText(vm.busy ? getString(R.string.calendar_busy) : status);
        findViewById(R.id.calendar_choose).setEnabled(!vm.busy);
        findViewById(R.id.calendar_sync).setEnabled(!vm.busy && vm.manager.permitted());
        findViewById(R.id.calendar_sync).setVisibility(vm.manager.enabled() || vm.manager.cleanupPending()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        ((TextView)findViewById(R.id.calendar_sync)).setText(vm.manager.cleanupPending()
                ? R.string.calendar_cleanup_action : R.string.calendar_sync);
        findViewById(R.id.calendar_disable).setVisibility(vm.manager.enabled() || vm.manager.cleanupPending()
                ? android.view.View.VISIBLE : android.view.View.GONE);
        findViewById(R.id.calendar_disable).setEnabled(!vm.busy && (vm.manager.enabled() || vm.manager.cleanupPending()));
    }
}
