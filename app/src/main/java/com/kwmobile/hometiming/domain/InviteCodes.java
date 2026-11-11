package com.kwmobile.hometiming.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Six-digit invite codes, valid for 24 hours from their server creation time (docs/MVP.md,
 * docs/DATA_MODEL.md invites). The security rules apply the same expiry with the server clock; the
 * app's check only decides what to show.
 */
public final class InviteCodes {
    public static final Duration LIFETIME = Duration.ofHours(24);

    private static final Pattern CODE = Pattern.compile("[0-9]{6}");

    private InviteCodes() {
    }

    /** A code from 000000 to 999999; pass a {@code SecureRandom} in the app. */
    public static String generate(Random random) {
        Objects.requireNonNull(random, "random");
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    /** Whether typed input has the code shape (surrounding spaces are not trimmed). */
    public static boolean isValid(String code) {
        return code != null && CODE.matcher(code).matches();
    }

    public static Instant expiresAt(Instant createdAt) {
        return createdAt.plus(LIFETIME);
    }

    /** Live strictly before createdAt + 24 h, matching the rules' {@code request.time <} check. */
    public static boolean isLive(Instant createdAt, Instant now) {
        return now.isBefore(expiresAt(createdAt));
    }
}
