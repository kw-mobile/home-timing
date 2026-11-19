package com.kwmobile.hometiming.data.update;

import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateInfoTest {
    private String valid() { return "{\"versionName\":\"0.4.1\",\"versionCode\":7,\"sizeBytes\":88000000,\"sha256\":\"" + "a".repeat(64)
            + "\",\"apkUrl\":\"https://github.com/kw-mobile/home-timing/releases/download/v0.4.1/home-timing-v0.4.1.apk\"}"; }
    private void rejects(String json) { assertThrows(IllegalArgumentException.class, () -> UpdateInfo.parse(json)); }
    @Test public void officialMetadataRoundTripsAndOnlyNewerCodeUpdates() {
        UpdateInfo value = UpdateInfo.parse(valid());
        assertTrue(value.isNewerThan(6)); assertFalse(value.isNewerThan(7)); assertFalse(value.isNewerThan(8));
        assertEquals(value.apkUrl, UpdateInfo.parse(value.json()).apkUrl);
    }
    @Test public void externalOrDowngradedTransportCannotSupplyAnApk() {
        rejects(valid().replace("https://github.com", "http://github.com"));
        rejects(valid().replace("https://github.com", "https://github.com.evil.invalid"));
        rejects(valid().replace("kw-mobile/home-timing", "another/repository"));
        rejects(valid().replace("home-timing-v0.4.1.apk", "other.apk"));
    }
    @Test public void versionAndHashMustBeStrict() {
        rejects(valid().replace("0.4.1", "../0.4.1")); rejects(valid().replace("0.4.1", "0.4.1-beta"));
        rejects(valid().replace("\"versionCode\":7", "\"versionCode\":7.5"));
        rejects(valid().replace("\"versionCode\":7", "\"versionCode\":0"));
        rejects(valid().replace("a".repeat(64), "x".repeat(64)));
    }
    @Test public void corruptMissingAndOversizedMetadataAreRejected() {
        rejects("not json"); rejects("{}"); rejects(valid().replace("88000000", "0"));
        rejects(valid().replace("88000000", "9999999999")); rejects(valid().replace("\"versionCode\":7", "\"versionCode\":2147483648"));
    }
}
