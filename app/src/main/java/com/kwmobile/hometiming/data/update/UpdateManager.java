package com.kwmobile.hometiming.data.update;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.kwmobile.hometiming.BuildConfig;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/** Application-scoped update work. Only verified same-signer self updates reach PackageInstaller. */
public final class UpdateManager {
    public enum Stage { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, VERIFYING, READY, INSTALLING, CONFIRM, ERROR }
    public enum Error { NETWORK, DOWNLOAD, VERIFY, INSTALL }
    public static final class State {
        public final Stage stage; public final UpdateInfo info; public final int progress; public final Error error;
        State(Stage stage, UpdateInfo info, int progress, Error error) { this.stage = stage; this.info = info; this.progress = progress; this.error = error; }
        public boolean busy() { return stage == Stage.CHECKING || stage == Stage.DOWNLOADING || stage == Stage.VERIFYING || stage == Stage.INSTALLING; }
    }
    private static volatile UpdateManager instance;
    private final Context app;
    private final SharedPreferences prefs;
    private final DownloadManager downloads;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private final MutableLiveData<State> changes = new MutableLiveData<>(new State(Stage.IDLE, null, 0, null));
    private UpdateInfo info;
    private File readyFile;
    private long downloadId = -1;
    private int queryGeneration = -1;
    private boolean screenVisible;
    private Intent confirmation;

    public static UpdateManager get(Context context) {
        if (instance == null) synchronized (UpdateManager.class) { if (instance == null) instance = new UpdateManager(context.getApplicationContext()); }
        return instance;
    }
    private UpdateManager(Context app) {
        this.app = app; prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE);
        downloads = app.getSystemService(DownloadManager.class);
        downloadId = prefs.getLong("download_id", -1);
        try {
            String oldName = prefs.getString("ready_file", "");
            if (!oldName.isEmpty() && oldName.equals(new File(oldName).getName()) && oldName.endsWith(".apk")) readyFile = new File(directory(), oldName);
            String saved = prefs.getString("info", "");
            if (!saved.isEmpty()) info = UpdateInfo.parse(saved);
            if (info != null && !info.isNewerThan(BuildConfig.VERSION_CODE)) { clearSaved(); publish(Stage.CURRENT, 0, null); }
            else if (info != null) {
                downloadId = prefs.getLong("download_id", -1);
                String name = prefs.getString("ready_file", "");
                if (!name.isEmpty() && name.equals(new File(name).getName()) && name.endsWith(".apk")) {
                    File file = new File(directory(), name);
                    if (file.isFile()) { readyFile = file; verifyRestored(); }
                    else publish(downloadId > 0 ? Stage.DOWNLOADING : Stage.AVAILABLE, 0, null);
                } else publish(downloadId > 0 ? Stage.DOWNLOADING : Stage.AVAILABLE, 0, null);
            }
        } catch (RuntimeException | IOException ignored) { clearSaved(); }
        File retained = readyFile;
        io.execute(() -> {
            try {
                File[] files = directory().listFiles();
                if (files != null) for (File file : files)
                    if (file.getName().startsWith("download-") && !file.equals(retained)) file.delete();
            } catch (IOException ignored) { }
        });
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        ContextCompat.registerReceiver(app, new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == downloadId && downloadId > 0) refreshDownload();
            }
        }, filter, ContextCompat.RECEIVER_EXPORTED);
    }
    public LiveData<State> changes() { return changes; }
    public State current() { return changes.getValue(); }
    public boolean autoEnabled() { return prefs.getBoolean("auto", true); }
    public void setAutoEnabled(boolean enabled) { prefs.edit().putBoolean("auto", enabled).apply(); }
    public void setScreenVisible(boolean visible) { screenVisible = visible; }
    public void onForeground() {
        refreshDownload();
        if (!BuildConfig.DEBUG && autoEnabled() && !current().busy()
                && current().stage != Stage.CONFIRM && System.currentTimeMillis() - prefs.getLong("checked_at", 0) >= 6 * 60 * 60 * 1000L) check();
    }
    public boolean shouldPrompt() {
        if (BuildConfig.DEBUG || screenVisible || !autoEnabled() || info == null) return false;
        if (current().stage == Stage.CONFIRM && confirmation != null) return true;
        return (current().stage == Stage.AVAILABLE || current().stage == Stage.READY)
                && (prefs.getLong("prompt_code", -1) != info.versionCode
                || System.currentTimeMillis() - prefs.getLong("prompt_at", 0) >= 24 * 60 * 60 * 1000L);
    }
    public void markPrompted() { if (info != null) prefs.edit().putLong("prompt_code", info.versionCode).putLong("prompt_at", System.currentTimeMillis()).apply(); }
    public void check() {
        if (current().busy() || current().stage == Stage.CONFIRM) return;
        int request = generation.incrementAndGet(); publish(Stage.CHECKING, 0, null);
        prefs.edit().putLong("checked_at", System.currentTimeMillis()).apply();
        io.execute(() -> {
            try {
                UpdateInfo latest = new UpdateFeed().latest();
                main.post(() -> {
                    if (request != generation.get()) return;
                    if (!latest.isNewerThan(BuildConfig.VERSION_CODE)) { clearSaved(); publish(Stage.CURRENT, 0, null); return; }
                    boolean same = info != null && info.sha256.equals(latest.sha256);
                    if (!same && readyFile != null) { readyFile.delete(); readyFile = null; prefs.edit().remove("ready_file").apply(); }
                    info = latest; prefs.edit().putString("info", info.json()).apply();
                    publish(same && readyFile != null && readyFile.isFile() ? Stage.READY : Stage.AVAILABLE, 0, null);
                });
            } catch (IOException | RuntimeException ignored) { main.post(() -> { if (request == generation.get()) publish(Stage.ERROR, 0, Error.NETWORK); }); }
        });
    }
    public void download() {
        if (info == null || current().busy() || current().stage == Stage.CONFIRM || !info.isNewerThan(BuildConfig.VERSION_CODE)) return;
        if (downloads == null) { publish(Stage.ERROR, 0, Error.DOWNLOAD); return; }
        try {
            if (downloadId > 0) removeDownload(downloadId);
            File base = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (base == null) throw new IOException("Download storage unavailable");
            File target = new File(base, "updates/release-" + info.versionCode + ".apk");
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Download directory unavailable");
            if (target.exists() && !target.delete()) throw new IOException("Old update unavailable");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.apkUrl))
                    .setTitle("때맞춤 " + info.versionName).setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setVisibleInDownloadsUi(false)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "updates/" + target.getName());
            generation.incrementAndGet(); downloadId = downloads.enqueue(request);
            prefs.edit().putLong("download_id", downloadId).putString("info", info.json()).apply();
            publish(Stage.DOWNLOADING, 0, null);
        } catch (IOException | RuntimeException ignored) { publish(Stage.ERROR, 0, Error.DOWNLOAD); }
    }
    public void refreshDownload() {
        if (downloadId <= 0 || downloads == null || queryGeneration == generation.get() || info == null || current().stage == Stage.VERIFYING) return;
        long id = downloadId; int request = generation.get(); queryGeneration = request; UpdateInfo release = info;
        io.execute(() -> {
            int status = DownloadManager.STATUS_FAILED, progress = 0;
            try (Cursor cursor = downloads.query(new DownloadManager.Query().setFilterById(id))) {
                if (cursor != null && cursor.moveToFirst()) {
                    status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    long got = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    progress = total > 0 ? (int) Math.min(100, 100 * got / total) : 0;
                }
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    main.post(() -> { if (request == generation.get()) publish(Stage.VERIFYING, 100, null); });
                    File file = verifiedCopy(id, release, request);
                    main.post(() -> {
                        finishQuery(request);
                        if (request != generation.get()) { file.delete(); return; }
                        readyFile = file; downloadId = -1; removeDownload(id);
                        prefs.edit().remove("download_id").putString("ready_file", file.getName()).apply(); publish(Stage.READY, 100, null);
                    }); return;
                }
                int finalStatus = status, finalProgress = progress;
                main.post(() -> { finishQuery(request); if (request == generation.get()) publish(finalStatus == DownloadManager.STATUS_FAILED ? Stage.ERROR : Stage.DOWNLOADING, finalProgress, finalStatus == DownloadManager.STATUS_FAILED ? Error.DOWNLOAD : null); });
            } catch (IOException | RuntimeException ignored) {
                main.post(() -> { finishQuery(request); if (request == generation.get()) { downloadId = -1; removeDownload(id); prefs.edit().remove("download_id").apply(); publish(Stage.ERROR, 0, Error.VERIFY); } });
            }
        });
    }
    private File verifiedCopy(long id, UpdateInfo release, int request) throws IOException {
        Uri uri = downloads.getUriForDownloadedFile(id); if (uri == null) throw new IOException("Missing download");
        File file = File.createTempFile("download-", ".apk", directory()); boolean valid = false;
        try {
            try (InputStream input = app.getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(file)) {
                if (input == null) throw new IOException("Missing download stream");
                byte[] buffer = new byte[65536]; int count; long total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count; if (total > release.sizeBytes || request != generation.get()) throw new IOException("Download canceled or oversized");
                    output.write(buffer, 0, count);
                }
            }
            ApkVerifier.verify(app, file, release); valid = true; return file;
        } finally { if (!valid) file.delete(); }
    }
    private void verifyRestored() {
        int request = generation.incrementAndGet(); File file = readyFile; UpdateInfo release = info;
        publish(Stage.VERIFYING, 100, null);
        io.execute(() -> {
            try { ApkVerifier.verify(app, file, release); main.post(() -> { if (request == generation.get()) publish(confirmation != null ? Stage.CONFIRM : Stage.READY, 100, null); }); }
            catch (IOException | RuntimeException ignored) { main.post(() -> { if (request == generation.get()) { file.delete(); readyFile = null; publish(Stage.ERROR, 0, Error.VERIFY); } }); }
        });
    }
    public boolean hasVerifiedFile() { return readyFile != null && readyFile.isFile(); }
    public void cancelDownload() {
        if (current().stage == Stage.INSTALLING || current().stage == Stage.CONFIRM) return;
        generation.incrementAndGet(); queryGeneration = -1;
        if (downloadId > 0 && downloads != null) removeDownload(downloadId);
        downloadId = -1; prefs.edit().remove("download_id").apply(); publish(info == null ? Stage.IDLE : Stage.AVAILABLE, 0, null);
    }
    public void install() {
        if (info == null || !hasVerifiedFile() || current().busy()) return;
        int request = generation.incrementAndGet(); UpdateInfo release = info; File file = readyFile;
        publish(Stage.INSTALLING, 100, null);
        io.execute(() -> {
            int sessionId = -1;
            try {
                ApkVerifier.verify(app, file, release);
                PackageInstaller installer = app.getPackageManager().getPackageInstaller();
                int previous = prefs.getInt("install_id", -1);
                prefs.edit().remove("install_id").apply();
                if (previous >= 0) try { installer.abandonSession(previous); } catch (RuntimeException ignored) { }
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(app.getPackageName()); params.setSize(file.length());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                sessionId = installer.createSession(params); prefs.edit().putInt("install_id", sessionId).apply();
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream input = new FileInputStream(file); OutputStream output = session.openWrite("base.apk", 0, file.length())) {
                        byte[] buffer = new byte[65536]; int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                        session.fsync(output);
                    }
                    Intent result = new Intent(app, UpdateInstallReceiver.class).setAction(app.getPackageName() + ".UPDATE_RESULT");
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    session.commit(PendingIntent.getBroadcast(app, sessionId, result, flags).getIntentSender());
                }
            } catch (IOException | RuntimeException ignored) {
                if (sessionId >= 0) try { app.getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch (RuntimeException ignoredAgain) { }
                main.post(() -> { if (request == generation.get()) { prefs.edit().remove("install_id").apply(); publish(Stage.ERROR, 100, Error.INSTALL); } });
            }
        });
    }
    public void installationResult(Intent result) {
        if (result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -2) != prefs.getInt("install_id", -1)) return;
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            confirmation = IntentCompat.getParcelableExtra(result, Intent.EXTRA_INTENT, Intent.class);
            if (confirmation != null) publish(Stage.CONFIRM, 100, null); else publish(Stage.ERROR, 100, Error.INSTALL);
        } else if (status == PackageInstaller.STATUS_SUCCESS) { clearSaved(); publish(Stage.CURRENT, 0, null); }
        else { confirmation = null; prefs.edit().remove("install_id").apply(); publish(Stage.ERROR, 100, Error.INSTALL); }
    }
    public Intent takeConfirmation() { Intent value = confirmation; confirmation = null; return value; }
    private File directory() throws IOException {
        File directory = new File(app.getCacheDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Update cache unavailable");
        return directory;
    }
    private void finishQuery(int request) { if (queryGeneration == request) queryGeneration = -1; }
    private void removeDownload(long id) {
        if (id > 0 && downloads != null) try { downloads.remove(id); } catch (RuntimeException ignored) { }
    }
    private void clearSaved() {
        if (downloadId > 0 && downloads != null) removeDownload(downloadId);
        if (readyFile != null) readyFile.delete();
        info = null; readyFile = null; downloadId = -1; confirmation = null;
        prefs.edit().remove("info").remove("download_id").remove("ready_file").remove("install_id").apply();
    }
    private void publish(Stage stage, int progress, Error error) { changes.setValue(new State(stage, info, progress, error)); }
}
