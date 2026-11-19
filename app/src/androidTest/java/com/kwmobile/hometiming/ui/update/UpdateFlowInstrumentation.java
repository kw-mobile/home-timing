package com.kwmobile.hometiming.ui.update;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.Lifecycle;
import com.kwmobile.hometiming.BuildConfig;
import com.kwmobile.hometiming.R;
import com.kwmobile.hometiming.data.update.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Local test signer fixtures only; never commits an install or touches Firebase accounts. */
public final class UpdateFlowInstrumentation extends Instrumentation {
    private Bundle args;
    private UpdateActivity activity;
    @Override public void onCreate(Bundle args) { this.args = args == null ? new Bundle() : args; super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle out = new Bundle(); StringBuilder results = new StringBuilder(); boolean passed = false;
        try {
            check(BuildConfig.DEBUG && ("ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE)), "dedicated debug AVD required");
            if ("tamper".equals(args.getString("mode"))) {
                android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("app_updates", 0);
                File ready = new File(new File(getTargetContext().getCacheDir(), "updates"), prefs.getString("ready_file", "missing"));
                check(ready.isFile(), "saved update fixture exists");
                try (RandomAccessFile file = new RandomAccessFile(ready, "rw")) { file.seek(0); file.write(0); }
                AtomicReference<UpdateManager> ref = new AtomicReference<>(); runOnMainSync(() -> ref.set(UpdateManager.get(getTargetContext())));
                await(() -> ref.get().current().stage == UpdateManager.Stage.ERROR, "cold-start tampered cache rejected");
                check(ref.get().current().error == UpdateManager.Error.VERIFY && !ready.exists(), "invalid cached apk removed");
                results.append("PASS process restart re-verifies and removes tampered cached update\n");
            } else {
                File fixtures = new File(getTargetContext().getFilesDir(), "fixtures");
                File valid = new File(fixtures, "valid.apk"), foreignSigner = new File(fixtures, "official-old.apk");
                check(valid.isFile() && foreignSigner.isFile(), "explicit local APK fixtures required");
                UpdateInfo release = info(valid, "0.4.1", 2);
                ApkVerifier.verify(getTargetContext(), valid, release);
                results.append("PASS actual non-debug APK with same test signer/new version\n");
                rejected(() -> ApkVerifier.verify(getTargetContext(), foreignSigner, info(foreignSigner, "0.3.0", 5)), "official signer differs from installed debug signer");
                rejected(() -> ApkVerifier.verify(getTargetContext(), valid, info(valid, "0.4.2", 2)), "manifest version cannot differ from metadata");
                File corrupt = new File(fixtures, "corrupt.apk"); copy(valid, corrupt);
                try (RandomAccessFile file = new RandomAccessFile(corrupt, "rw")) { file.seek(0); file.write(0); }
                rejected(() -> ApkVerifier.verify(getTargetContext(), corrupt, release), "tampered bytes rejected"); corrupt.delete();
                results.append("PASS different signer, version mismatch, and tampered bytes rejected before install\n");
                File directory = new File(getTargetContext().getCacheDir(), "updates"); directory.mkdirs();
                File ready = new File(directory, "qa-ready.apk"); copy(valid, ready);
                check(getTargetContext().getSharedPreferences("app_updates", 0).edit().putString("info", release.json())
                        .putString("ready_file", ready.getName()).remove("download_id").commit(), "saved pending update fixture");
                AtomicReference<UpdateManager> ref = new AtomicReference<>(); runOnMainSync(() -> ref.set(UpdateManager.get(getTargetContext())));
                UpdateManager manager = ref.get(); await(() -> manager.current().stage == UpdateManager.Stage.READY, "saved valid update reverified");
                results.append("PASS saved verified APK recovers as ready\n");
                activity = (UpdateActivity)startActivitySync(new Intent(getTargetContext(), UpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                await(() -> visible().contains("설치할 준비가 됐어요"), "update screen ready");
                runOnMainSync(() -> ((com.google.android.material.switchmaterial.SwitchMaterial)activity.findViewById(R.id.update_auto)).setChecked(false));
                check(!manager.autoEnabled(), "automatic checks can be disabled");
                AtomicReference<UpdateActivity> replacement = new AtomicReference<>();
                Application app = (Application)getTargetContext().getApplicationContext();
                Application.ActivityLifecycleCallbacks callback = new Application.ActivityLifecycleCallbacks() {
                    public void onActivityCreated(Activity a, Bundle b) { if (a instanceof UpdateActivity) replacement.set((UpdateActivity)a); }
                    public void onActivityStarted(Activity a) {} public void onActivityResumed(Activity a) {} public void onActivityPaused(Activity a) {}
                    public void onActivityStopped(Activity a) {} public void onActivitySaveInstanceState(Activity a, Bundle b) {} public void onActivityDestroyed(Activity a) {}
                };
                app.registerActivityLifecycleCallbacks(callback);
                try {
                    runOnMainSync(activity::recreate); await(() -> replacement.get() != null && replacement.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED), "update activity recreated");
                    activity = replacement.get();
                } finally { app.unregisterActivityLifecycleCallbacks(callback); }
                check(!manager.autoEnabled() && manager.current().stage == UpdateManager.Stage.READY, "setting/file survive recreation");
                runOnMainSync(() -> ((com.google.android.material.switchmaterial.SwitchMaterial)activity.findViewById(R.id.update_auto)).setChecked(true));
                check(!getTargetContext().getPackageManager().canRequestPackageInstalls(), "install permission denied for this scenario");
                int sessions = getTargetContext().getPackageManager().getPackageInstaller().getMySessions().size();
                runOnMainSync(() -> activity.findViewById(R.id.update_action).performClick());
                await(() -> visible().contains("업데이트 설치 허용"), "installation permission explanation");
                screenshot("install-permission.png");
                getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
                await(() -> !visible().contains("업데이트 설치 허용"), "permission dialog canceled");
                check(manager.current().stage == UpdateManager.Stage.READY
                        && sessions == getTargetContext().getPackageManager().getPackageInstaller().getMySessions().size(), "cancel creates no install session");
                screenshot("update-ready.png");
                results.append("PASS automatic-check setting/recreation, unknown-source permission and cancel without installation\n");
            }
            passed = true;
        } catch (Throwable error) { results.append("FAIL ").append(error).append(" visible=").append(visible()).append('\n'); }
        finally { if (activity != null) runOnMainSync(activity::finish); }
        out.putString("stream", results + "UPDATE FLOW " + (passed ? "PASS" : "FAIL") + ": local fixtures, not official auto-update installation\n");
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, out);
    }
    private UpdateInfo info(File file, String version, long code) throws IOException {
        return UpdateInfo.parse("{\"versionName\":\"" + version + "\",\"versionCode\":" + code + ",\"sizeBytes\":" + file.length()
                + ",\"sha256\":\"" + ApkVerifier.sha256(file) + "\",\"apkUrl\":\"" + UpdateInfo.RELEASES + "download/v" + version + "/home-timing-v" + version + ".apk\"}");
    }
    private void copy(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
        }
    }
    private void screenshot(String name) throws IOException {
        waitForIdleSync(); SystemClock.sleep(300); android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "screenshot");
        try (FileOutputStream output = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output); }
        finally { bitmap.recycle(); }
    }
    private String visible() { StringBuilder out = new StringBuilder(); collect(getUiAutomation().getRootInActiveWindow(), out); return out.toString(); }
    private void collect(AccessibilityNodeInfo node, StringBuilder out) { if (node == null) return; if (node.getText() != null) out.append(node.getText()).append('\n'); for (int i=0;i<node.getChildCount();i++) collect(node.getChild(i),out); }
    private void await(BooleanSupplier test, String name) { long end=SystemClock.elapsedRealtime()+15000; while (!test.getAsBoolean() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(100); check(test.getAsBoolean(), name); }
    private void rejected(Checked work, String name) throws Exception { try { work.run(); throw new AssertionError(name); } catch (IOException expected) { } }
    private interface Checked { void run() throws Exception; }
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
}
