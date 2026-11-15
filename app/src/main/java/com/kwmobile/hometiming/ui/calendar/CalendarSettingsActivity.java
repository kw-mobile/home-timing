package com.kwmobile.hometiming.ui.calendar;

import android.Manifest;
import android.os.Bundle;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kwmobile.hometiming.R;

public final class CalendarSettingsActivity extends AppCompatActivity {
    private CalendarSettingsViewModel vm;
    private final ActivityResultLauncher<String[]> permissions = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> vm.refresh());
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_calendar_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.calendar_settings_root), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        vm = new ViewModelProvider(this).get(CalendarSettingsViewModel.class);
        findViewById(R.id.calendar_back).setOnClickListener(v -> finish());
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
        findViewById(R.id.calendar_disable).setEnabled(!vm.busy && (vm.manager.enabled() || vm.manager.cleanupPending()));
    }
}
