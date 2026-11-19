package com.kwmobile.hometiming.data.update;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import androidx.core.content.pm.PackageInfoCompat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public final class ApkVerifier {
    private ApkVerifier() {}

    public static void verify(Context context, File apk, UpdateInfo release) throws IOException {
        if (apk.length() != release.sizeBytes || !sha256(apk).equals(release.sha256)) throw new IOException("APK integrity mismatch");
        PackageManager pm = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        try {
            PackageInfo installed = pm.getPackageInfo(context.getPackageName(), flags);
            if (candidate == null || candidate.applicationInfo == null
                    || !context.getPackageName().equals(candidate.packageName)
                    || !release.versionName.equals(candidate.versionName)
                    || PackageInfoCompat.getLongVersionCode(candidate) != release.versionCode
                    || !release.isNewerThan(PackageInfoCompat.getLongVersionCode(installed))
                    || (candidate.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    || signers(candidate).isEmpty() || !signers(candidate).equals(signers(installed)))
                throw new IOException("APK package, version or signer mismatch");
        } catch (PackageManager.NameNotFoundException ignored) { throw new IOException("Installed app unavailable"); }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo info) throws IOException {
        Signature[] values = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                ? info.signingInfo.getApkContentsSigners() : info.signatures;
        Set<String> result = new HashSet<>();
        if (values != null) for (Signature value : values) result.add(hex(digest().digest(value.toByteArray())));
        return result;
    }

    public static String sha256(File file) throws IOException {
        MessageDigest digest = digest();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return hex(digest.digest());
    }
    private static MessageDigest digest() throws IOException {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 unavailable"); }
    }
    private static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }
}
