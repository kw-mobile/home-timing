package com.kwmobile.hometiming.ui.update;

import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.update.UpdateManager;

public final class UpdateActivity extends AppCompatActivity {
    public static final String AUTO_START = "start_update";
    private UpdateManager updates;
    private boolean installWhenReady, permissionDialogVisible;
    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { updates.refreshDownload(); ticker.postDelayed(this, 1000); }
    };
    private final androidx.activity.result.ActivityResultLauncher<Intent> permission = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (getPackageManager().canRequestPackageInstalls()) updates.install();
                else render(updates.current());
            });
    private final androidx.activity.result.ActivityResultLauncher<Intent> installer = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> render(updates.current()));

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); WindowCompat.setDecorFitsSystemWindows(getWindow(), false); setContentView(R.layout.activity_update);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.feature_heading), true);
        View root = findViewById(R.id.update_root);
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat barsController = WindowCompat.getInsetsController(getWindow(), root);
        barsController.setAppearanceLightStatusBars(!night); barsController.setAppearanceLightNavigationBars(!night);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        updates = UpdateManager.get(this);
        installWhenReady = saved == null ? getIntent().getBooleanExtra(AUTO_START, false) : saved.getBoolean(AUTO_START);
        ((TextView)findViewById(R.id.update_version)).setText(getString(R.string.update_current_version, BuildConfig.VERSION_NAME));
        SwitchMaterial auto = findViewById(R.id.update_auto); auto.setChecked(updates.autoEnabled());
        auto.setOnCheckedChangeListener((button, checked) -> updates.setAutoEnabled(checked));
        findViewById(R.id.update_back).setOnClickListener(view -> finish());
        findViewById(R.id.update_check).setOnClickListener(view -> updates.check());
        findViewById(R.id.update_cancel).setOnClickListener(view -> { installWhenReady = false; updates.cancelDownload(); });
        findViewById(R.id.update_action).setOnClickListener(view -> action());
        updates.changes().observe(this, this::render);
        if (installWhenReady && updates.current().stage == UpdateManager.Stage.AVAILABLE) updates.download();
        else if (updates.current().stage == UpdateManager.Stage.IDLE || updates.current().stage == UpdateManager.Stage.CURRENT) updates.check();
    }
    @Override protected void onResume() {
        super.onResume(); updates.setScreenVisible(true); ticker.post(tick); render(updates.current());
    }
    @Override protected void onPause() {
        ticker.removeCallbacksAndMessages(null); updates.setScreenVisible(false); super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean(AUTO_START, installWhenReady); super.onSaveInstanceState(out); }
    private void action() {
        UpdateManager.State state = updates.current();
        if (state.busy()) return;
        installWhenReady = true;
        if (updates.hasVerifiedFile()) beginInstall();
        else if (state.info != null) updates.download();
        else updates.check();
    }
    private void beginInstall() {
        installWhenReady = false;
        Intent pending = updates.takeConfirmation();
        if (pending != null) { installer.launch(pending); return; }
        if (getPackageManager().canRequestPackageInstalls()) { updates.install(); return; }
        if (permissionDialogVisible) return;
        permissionDialogVisible = true;
        new MaterialAlertDialogBuilder(this).setTitle(R.string.update_permission_title).setMessage(R.string.update_permission_help)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.update_permission_open, (dialog, which) -> {
                    try { permission.launch(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", getPackageName(), null))); }
                    catch (android.content.ActivityNotFoundException ignored) { render(updates.current()); }
                }).setOnDismissListener(dialog -> permissionDialogVisible = false).show();
    }
    private void render(UpdateManager.State state) {
        if (state == null || isFinishing()) return;
        int text;
        switch (state.stage) {
            case CHECKING: text = R.string.update_checking; break;
            case CURRENT: text = R.string.update_latest; break;
            case DOWNLOADING: text = R.string.update_downloading; break;
            case VERIFYING: text = R.string.update_verifying; break;
            case READY: text = R.string.update_ready; break;
            case INSTALLING: text = R.string.update_installing; break;
            case CONFIRM: text = R.string.update_confirm; break;
            case ERROR:
                text = state.error == UpdateManager.Error.VERIFY ? R.string.update_error_verify
                        : state.error == UpdateManager.Error.DOWNLOAD ? R.string.update_error_download
                        : state.error == UpdateManager.Error.INSTALL ? R.string.update_error_install : R.string.update_error_network; break;
            case AVAILABLE: text = R.string.update_found; break;
            default: text = R.string.update_idle;
        }
        TextView target = findViewById(R.id.update_target);
        target.setVisibility(state.info == null ? View.GONE : View.VISIBLE);
        if (state.info != null) target.setText(getString(R.string.update_available, state.info.versionName, (state.info.sizeBytes + 1048575) / 1048576));
        String message = state.stage == UpdateManager.Stage.DOWNLOADING ? getString(R.string.update_downloading, state.progress) : getString(text);
        ((TextView)findViewById(R.id.update_status)).setText(message);
        LinearProgressIndicator progress = findViewById(R.id.update_progress);
        boolean indeterminate = state.stage != UpdateManager.Stage.DOWNLOADING;
        if (progress.isIndeterminate() != indeterminate) {
            progress.setVisibility(View.INVISIBLE); progress.setIndeterminate(indeterminate);
        }
        progress.setVisibility(state.busy() ? View.VISIBLE : View.GONE);
        if (!progress.isIndeterminate()) progress.setProgressCompat(state.progress, true);
        MaterialButton action = findViewById(R.id.update_action);
        action.setText(updates.hasVerifiedFile() ? R.string.update_install : R.string.update_download);
        action.setVisibility(state.info != null ? View.VISIBLE : View.GONE); action.setEnabled(!state.busy());
        findViewById(R.id.update_check).setEnabled(!state.busy() && state.stage != UpdateManager.Stage.CONFIRM);
        findViewById(R.id.update_cancel).setVisibility(state.stage == UpdateManager.Stage.DOWNLOADING || state.stage == UpdateManager.Stage.VERIFYING ? View.VISIBLE : View.GONE);
        if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            if (state.stage == UpdateManager.Stage.CONFIRM) {
                Intent pending = updates.takeConfirmation(); if (pending != null) { installWhenReady = false; installer.launch(pending); }
            } else if (installWhenReady && state.stage == UpdateManager.Stage.READY) beginInstall();
            else if (installWhenReady && state.stage == UpdateManager.Stage.AVAILABLE) updates.download();
        }
    }
}
