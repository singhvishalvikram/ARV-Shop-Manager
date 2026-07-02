package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.local.SessionStore;
import com.arvshop.admin.data.model.AuthUser;
import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONException;
import org.json.JSONObject;

/** Login / logout / session bootstrap against /api/v1/auth. */
public class AuthRepository {

    private final ApiClient api;
    private final SessionStore session;

    public AuthRepository(ApiClient api, SessionStore session) {
        this.api = api;
        this.session = session;
    }

    public boolean isLoggedIn() {
        return session.isLoggedIn();
    }

    public String ownerName() {
        return session.name();
    }

    /** Blocking; call off the main thread. Persists the token on success. */
    public AuthUser login(String phone, String password) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("password", password);
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Invalid input", 0);
        }
        JSONObject data = api.postObject("/api/v1/auth/login", body);
        return persist(data);
    }

    public AuthUser signup(String phone, String password, String name) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("password", password);
            body.put("name", name);
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Invalid input", 0);
        }
        JSONObject data = api.postObject("/api/v1/auth/signup", body);
        return persist(data);
    }

    public void logout() {
        try {
            api.postObject("/api/v1/auth/logout", new JSONObject());
        } catch (ApiException ignored) {
            // Best-effort server revoke; always clear locally regardless.
        }
        session.clear();
    }

    private AuthUser persist(JSONObject data) throws ApiException {
        String token = data.optString("token", "");
        JSONObject userObj = data.optJSONObject("user");
        if (token.isEmpty() || userObj == null) {
            throw new ApiException(ApiException.CODE_PARSE, "Malformed login response", 0);
        }
        AuthUser user = AuthUser.fromJson(userObj);
        session.save(token, user.id, user.phone, user.name, user.role);
        return user;
    }
}
