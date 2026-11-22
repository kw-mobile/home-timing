package com.kwmobile.hometiming.ui.recognition;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.*;
import android.util.Size;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.*;
import androidx.lifecycle.*;
import com.google.android.material.button.MaterialButton;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.domain.ProductNameCandidates;

/** Optional on-device name input. Only explicit confirmation returns a name to the editor. */
public final class PhotoNameActivity extends AppCompatActivity {
    public static final String REQUEST = "photo_request", NAME = "photo_name", OLD_NAME = "photo_old_name";
    private PhotoNameViewModel vm;
    private ProcessCameraProvider provider;
    private ImageCapture capture;
    private int cameraGeneration;
    private boolean permissionRequested;
    private final ActivityResultLauncher<String> permission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { if (granted) startCamera(); else vm.fail(R.string.ocr_permission_denied); });
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (getIntent().getStringExtra(REQUEST) == null) { finish(); return; }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_photo_name);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.feature_heading), true);
        boolean light = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                != android.content.res.Configuration.UI_MODE_NIGHT_YES;
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.ocr_root)).setAppearanceLightStatusBars(light);
        WindowCompat.getInsetsController(getWindow(), findViewById(R.id.ocr_root)).setAppearanceLightNavigationBars(light);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ocr_root), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        vm = new ViewModelProvider(this).get(PhotoNameViewModel.class);
        String oldName = getIntent().getStringExtra(OLD_NAME);
        ((TextView)findViewById(R.id.ocr_old_name)).setText(getString(R.string.ocr_old_name, oldName == null ? "" : oldName));
        findViewById(R.id.ocr_old_name).setVisibility(oldName == null || oldName.trim().isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.ocr_back).setOnClickListener(v -> finish());
        findViewById(R.id.ocr_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.ocr_retry).setOnClickListener(v -> { vm.ready(); startCamera(); });
        findViewById(R.id.ocr_shutter).setOnClickListener(v -> takePhoto());
        EditText name = findViewById(R.id.ocr_name);
        name.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int f) { }
            public void onTextChanged(CharSequence s,int a,int b,int c) { vm.selected = s.toString(); updateSelection(); }
            public void afterTextChanged(Editable e) { }
        });
        findViewById(R.id.ocr_apply).setOnClickListener(v -> {
            String selected = ProductNameCandidates.normalize(name.getText().toString());
            if (selected.isEmpty() || selected.length() > 60) { name.setError(getString(R.string.ocr_name_error)); return; }
            if (!vm.result || vm.busy) return;
            setResult(Activity.RESULT_OK, new Intent().putExtra(REQUEST, getIntent().getStringExtra(REQUEST)).putExtra(NAME, selected));
            finish();
        });
        vm.changes().observe(this, ignored -> render());
        permissionRequested = saved != null && saved.getBoolean("camera_permission_requested");
    }
    @Override protected void onStart() {
        super.onStart();
        if (vm != null && !vm.result && (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED || !permissionRequested)) startCamera();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("camera_permission_requested", permissionRequested);
        super.onSaveInstanceState(out);
    }
    private void startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionRequested = true;
            permission.launch(Manifest.permission.CAMERA); return;
        }
        int token = ++cameraGeneration;
        var future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (token != cameraGeneration || isFinishing() || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) return;
            try {
                provider = future.get(); provider.unbindAll();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(((PreviewView)findViewById(R.id.ocr_preview)).getSurfaceProvider());
                capture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(new Size(1280, 960)).build();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture);
                render();
            } catch (Exception ignored) { capture = null; vm.fail(R.string.ocr_camera_unavailable); }
        }, ContextCompat.getMainExecutor(this));
    }
    private void takePhoto() {
        if (capture == null || vm.busy || vm.result) return;
        int token = vm.begin();
        try {
            capture.setTargetRotation(findViewById(R.id.ocr_preview).getDisplay().getRotation());
            capture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
                @Override public void onCaptureSuccess(ImageProxy image) { vm.analyze(token, image); }
                @Override public void onError(ImageCaptureException error) { if (vm.current(token)) vm.fail(R.string.ocr_camera_unavailable); }
            });
        } catch (RuntimeException ignored) { vm.fail(R.string.ocr_camera_unavailable); }
    }
    private void render() {
        ((TextView)findViewById(R.id.ocr_status)).setText(vm.message);
        boolean cameraUnavailable = vm.message == R.string.ocr_permission_denied || vm.message == R.string.ocr_camera_unavailable;
        findViewById(R.id.ocr_camera_box).setVisibility(vm.result || cameraUnavailable ? View.GONE : View.VISIBLE);
        findViewById(R.id.ocr_shutter).setVisibility(vm.result || cameraUnavailable ? View.GONE : View.VISIBLE);
        findViewById(R.id.ocr_shutter).setEnabled(capture != null && !vm.busy && !vm.result);
        findViewById(R.id.ocr_retry).setVisibility(vm.result || !vm.busy && vm.message != R.string.ocr_guide ? View.VISIBLE : View.GONE);
        findViewById(R.id.ocr_retry).setEnabled(!vm.busy);
        findViewById(R.id.ocr_result).setVisibility(vm.result ? View.VISIBLE : View.GONE);
        EditText name = findViewById(R.id.ocr_name);
        if (!name.getText().toString().equals(vm.selected)) name.setText(vm.selected);
        LinearLayout choices = findViewById(R.id.ocr_candidates); choices.removeAllViews();
        for (String candidate : vm.candidates) {
            MaterialButton button = (MaterialButton)getLayoutInflater().inflate(R.layout.component_preset_option, choices, false);
            button.setText(candidate); button.setTag(candidate); button.setCheckable(true);
            button.setOnClickListener(v -> vm.select(candidate));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.ht_space_8);
            choices.addView(button, params);
        }
        updateSelection();
    }
    private void updateSelection() {
        LinearLayout choices = findViewById(R.id.ocr_candidates);
        for (int i = 0; i < choices.getChildCount(); i++) {
            MaterialButton button = (MaterialButton)choices.getChildAt(i);
            boolean selected = vm.selected.equals(button.getTag());
            button.setChecked(selected);
            button.setIconResource(selected ? R.drawable.ic_check : 0);
            button.setIconTint(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ht_primary)));
            button.setStrokeWidth(selected ? Math.round(getResources().getDisplayMetrics().density * 2) : 0);
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ht_primary)));
        }
        findViewById(R.id.ocr_apply).setEnabled(vm.result && !vm.busy && !vm.selected.trim().isEmpty());
    }
    @Override protected void onStop() {
        cameraGeneration++; capture = null;
        if (provider != null) provider.unbindAll();
        if (vm != null && !isChangingConfigurations()) vm.cancelProcessing();
        super.onStop();
    }
}
