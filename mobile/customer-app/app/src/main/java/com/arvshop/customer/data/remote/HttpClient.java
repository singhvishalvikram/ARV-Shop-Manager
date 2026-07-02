package com.arvshop.customer.data.remote;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTPS GET client on top of the platform's HttpURLConnection —
 * intentionally no OkHttp/Retrofit (dependency-minimalism guardrail).
 * HTTPS is enforced by the manifest (usesCleartextTraffic=false); dev builds
 * pointing at localhost use the emulator's 10.0.2.2 over HTTP only in debug.
 */
public final class HttpClient {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_BYTES = 20 * 1024 * 1024; // hard cap: 20 MB

    private HttpClient() { }

    public static String getString(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept-Encoding", "identity");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + code + " for " + url);
            }
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192];
                int n;
                int total = 0;
                while ((n = in.read(chunk)) != -1) {
                    total += n;
                    if (total > MAX_BODY_BYTES) {
                        throw new IOException("Response exceeds size cap: " + url);
                    }
                    buf.write(chunk, 0, n);
                }
                return buf.toByteArray();
            }
        } finally {
            conn.disconnect();
        }
    }
}
