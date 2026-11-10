package com.kwmobile.hometiming.rules;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/**
 * Who a request comes from. The Firestore emulator accepts unsigned ID tokens, so a test user
 * needs no real Auth account; {@link #admin()} bypasses rules like the Admin SDK.
 */
final class Auth {
    private final String bearer;

    private Auth(String bearer) {
        this.bearer = bearer;
    }

    static Auth signedOut() {
        return new Auth(null);
    }

    /** Emulator-only admin access for seeding and inspecting data. Never a test subject. */
    static Auth admin() {
        return new Auth("owner");
    }

    /** A signed-in email/password user with the given uid. */
    static Auth user(String uid) {
        long now = Instant.now().getEpochSecond();
        String project = FirestoreEmulator.PROJECT_ID;
        String header = "{\"alg\":\"none\",\"kid\":\"fakekid\",\"typ\":\"JWT\"}";
        String payload = "{\"iss\":" + Json.quote("https://securetoken.google.com/" + project)
                + ",\"aud\":" + Json.quote(project)
                + ",\"iat\":" + now + ",\"exp\":" + (now + 3600) + ",\"auth_time\":" + now
                + ",\"sub\":" + Json.quote(uid) + ",\"user_id\":" + Json.quote(uid)
                + ",\"firebase\":{\"sign_in_provider\":\"password\",\"identities\":{}}}";
        return new Auth(base64Url(header) + "." + base64Url(payload) + ".");
    }

    /** Value for the Authorization header, or null when signed out. */
    String header() {
        return bearer == null ? null : "Bearer " + bearer;
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
