package com.kwmobile.hometiming.data.update;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;

/** Only the project's stable official release can become an install candidate. */
public final class UpdateInfo {
    public static final String RELEASES = "https://github.com/kw-mobile/home-timing/releases/";
    public static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    public final String versionName, apkUrl, sha256;
    public final long versionCode, sizeBytes;

    private UpdateInfo(String name, long code, long size, String url, String sha) {
        versionName = name; versionCode = code; sizeBytes = size; apkUrl = url; sha256 = sha;
    }

    public static UpdateInfo parse(String json) {
        try {
            JsonObject value = JsonParser.parseString(json).getAsJsonObject();
            String name = value.get("versionName").getAsString();
            long code = value.get("versionCode").getAsBigDecimal().longValueExact();
            long size = value.get("sizeBytes").getAsBigDecimal().longValueExact();
            String url = value.get("apkUrl").getAsString();
            String sha = value.get("sha256").getAsString().toLowerCase(Locale.ROOT);
            if (name.length() > 32 || !name.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
                    || code < 1 || code > Integer.MAX_VALUE || size < 1 || size > MAX_APK_BYTES
                    || !sha.matches("[0-9a-f]{64}")
                    || !url.equals(RELEASES + "download/v" + name + "/home-timing-v" + name + ".apk"))
                throw new IllegalArgumentException("Invalid official release metadata");
            return new UpdateInfo(name, code, size, url, sha);
        } catch (RuntimeException error) { throw new IllegalArgumentException("Invalid official release metadata", error); }
    }

    public boolean isNewerThan(long installedCode) { return versionCode > installedCode; }
    public String json() { return new Gson().toJson(this); }
}
