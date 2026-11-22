package com.kwmobile.hometiming.data.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

public final class UpdateFeed {
    public UpdateInfo latest() throws IOException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        URL url = new URL(UpdateInfo.RELEASES + "latest/download/update.json");
        for (int redirects = 0; redirects < 6; redirects++) {
            if (System.nanoTime() > deadline) throw new IOException("Release request timed out");
            if (!trusted(url)) throw new IOException("Untrusted release location");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
            connection.setInstanceFollowRedirects(false); connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "HomeTiming-Android-Updater");
            try {
                int status = connection.getResponseCode();
                if (status >= 300 && status <= 399) {
                    String next = connection.getHeaderField("Location");
                    if (next == null) throw new IOException("Missing release redirect");
                    url = new URL(url, next); continue;
                }
                if (status != 200) throw new IOException("Release metadata unavailable");
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (System.nanoTime() > deadline) throw new IOException("Release request timed out");
                        if (bytes.size() + count > 65_536) throw new IOException("Oversized release metadata");
                        bytes.write(buffer, 0, count);
                    }
                    try { return UpdateInfo.parse(bytes.toString(StandardCharsets.UTF_8.name())); }
                    catch (IllegalArgumentException ignored) { throw new IOException("Invalid release metadata"); }
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("Too many release redirects");
    }

    private boolean trusted(URL url) {
        String host = url.getHost();
        return "https".equals(url.getProtocol()) && ("github.com".equals(host)
                || host.endsWith(".githubusercontent.com"));
    }
}
