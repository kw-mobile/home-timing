package com.kwmobile.hometiming.rules;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.StringJoiner;

/**
 * Firestore emulator REST client for rules tests.
 *
 * <p>Only talks to a {@code demo-} project on the emulator named by {@code FIRESTORE_EMULATOR_HOST},
 * so a test can never read or change the shared Firebase project.
 */
final class FirestoreEmulator {
    static final String PROJECT_ID = projectId();

    /** HTTP status the emulator returns when security rules deny a request. */
    static final int DENIED = 403;
    static final int OK = 200;
    static final int NOT_FOUND = 404;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String origin;
    private final String documentsName;

    private FirestoreEmulator(String host) {
        origin = "http://" + host;
        documentsName = "projects/" + PROJECT_ID + "/databases/(default)/documents";
    }

    static FirestoreEmulator connect() {
        String host = System.getenv("FIRESTORE_EMULATOR_HOST");
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("FIRESTORE_EMULATOR_HOST is not set; run through firebase emulators:exec");
        }
        return new FirestoreEmulator(host);
    }

    /** Deletes every document in the emulator database. */
    void clear() {
        expect(OK, send("DELETE", origin + "/emulator/v1/" + documentsName, Auth.signedOut(), null),
                "clear emulator data");
    }

    /** Writes a document with admin access, bypassing rules. */
    void seed(String path, Fields fields) {
        expect(OK, set(Auth.admin(), path, fields), "seed " + path);
    }

    Response get(Auth auth, String path) {
        return send("GET", documentUrl(path), auth, null);
    }

    /** Creates or replaces a document. */
    Response set(Auth auth, String path, Fields fields) {
        return send("PATCH", documentUrl(path), auth, "{\"fields\":" + fields.toJson() + "}");
    }

    Response delete(Auth auth, String path) {
        return send("DELETE", documentUrl(path), auth, null);
    }

    /** Commits all writes atomically, as a batched write or transaction does. */
    Response commit(Auth auth, Write... writes) {
        StringJoiner joined = new StringJoiner(",", "{\"writes\":[", "]}");
        for (Write write : writes) {
            joined.add(write.toJson(documentsName));
        }
        return send("POST", origin + "/v1/" + documentsName + ":commit", auth, joined.toString());
    }

    private String documentUrl(String path) {
        return origin + "/v1/" + documentsName + "/" + path;
    }

    private Response send(String method, String url, Auth auth, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        String authorization = auth.header();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try {
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Firestore emulator unreachable at " + origin, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling the Firestore emulator", e);
        }
    }

    private static void expect(int status, Response response, String what) {
        if (response.status() != status) {
            throw new IllegalStateException("Could not " + what + ": " + response);
        }
    }

    private static String projectId() {
        String project = System.getenv("GCLOUD_PROJECT");
        if (project == null || project.isEmpty()) {
            project = "demo-home-timing";
        }
        if (!project.startsWith("demo-")) {
            throw new IllegalStateException("Rules tests only run on a demo- emulator project, not " + project);
        }
        return project;
    }

    /** HTTP status and body of one emulator call. */
    record Response(int status, String body) {
        @Override
        public String toString() {
            return status + " " + body;
        }
    }
}
