package com.arvshop.admin.data.remote;

import com.arvshop.admin.data.local.SessionStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client over HttpURLConnection (no OkHttp/Retrofit — supply-chain
 * minimalism, GUARDRAILS 6.2). Responsibilities:
 *  - attach {@code Authorization: Bearer <token>} from the session,
 *  - parse the universal envelope into data or a typed {@link ApiException},
 *  - clear the session on 401 so the UI can bounce to login.
 * All calls are synchronous and MUST be invoked off the main thread.
 */
public class ApiClient {

    public interface UnauthorizedListener {
        void onUnauthorized();
    }

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    private final String baseUrl;
    private final SessionStore session;
    private volatile UnauthorizedListener unauthorizedListener;

    public ApiClient(String baseUrl, SessionStore session) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.session = session;
    }

    public void setUnauthorizedListener(UnauthorizedListener l) {
        this.unauthorizedListener = l;
    }

    public JSONObject getObject(String path) throws ApiException {
        return Envelope.dataObject(request("GET", path, null, null), lastStatus.get());
    }

    public JSONArray getArray(String path) throws ApiException {
        String body = request("GET", path, null, null);
        Object data = Envelope.unwrap(body, lastStatus.get());
        if (data instanceof JSONArray) return (JSONArray) data;
        throw new ApiException(ApiException.CODE_PARSE, "Expected a list", lastStatus.get());
    }

    public JSONObject postObject(String path, JSONObject body) throws ApiException {
        return Envelope.dataObject(request("POST", path, body, null), lastStatus.get());
    }

    public JSONObject postObject(String path, JSONObject body, String idempotencyKey) throws ApiException {
        return Envelope.dataObject(request("POST", path, body, idempotencyKey), lastStatus.get());
    }

    public JSONObject putObject(String path, JSONObject body) throws ApiException {
        return Envelope.dataObject(request("PUT", path, body, null), lastStatus.get());
    }

    public void delete(String path) throws ApiException {
        Envelope.unwrap(request("DELETE", path, null, null), lastStatus.get());
    }

    // Per-thread status of the most recent request, so the envelope parser sees the
    // right HTTP code (client is used from a bounded pool; ThreadLocal keeps it safe).
    private final ThreadLocal<Integer> lastStatus = ThreadLocal.withInitial(() -> 0);

    private String request(String method, String path, JSONObject body, String idempotencyKey)
            throws ApiException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(baseUrl + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            String token = session.token();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (idempotencyKey != null) {
                conn.setRequestProperty("Idempotency-Key", idempotencyKey);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
            }

            int status = conn.getResponseCode();
            lastStatus.set(status);
            String responseBody = readBody(conn, status);

            if (status == 401) {
                session.clear();
                UnauthorizedListener l = unauthorizedListener;
                if (l != null) l.onUnauthorized();
            }
            return responseBody;
        } catch (IOException e) {
            throw new ApiException(ApiException.CODE_NETWORK,
                    "Network error. Check your connection.", 0);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn, int status) throws IOException {
        InputStream in = (status >= 200 && status < 300)
                ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) return "{}";
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
