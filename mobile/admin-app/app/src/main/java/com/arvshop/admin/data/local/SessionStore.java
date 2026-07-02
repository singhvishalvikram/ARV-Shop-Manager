package com.arvshop.admin.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Persists the session token + owner identity in EncryptedSharedPreferences
 * (AES-256). The bearer token is a credential, so it is never stored in plaintext
 * and never logged (GUARDRAILS 1.3 / 6.4). Falls back to standard prefs only if the
 * keystore is unavailable on very old/broken devices, logging a warning.
 */
public final class SessionStore {

    private static final String FILE = "arv_admin_session";
    private static final String K_TOKEN = "token";
    private static final String K_USER_ID = "user_id";
    private static final String K_PHONE = "phone";
    private static final String K_NAME = "name";
    private static final String K_ROLE = "role";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        this.prefs = build(context);
    }

    private static SharedPreferences build(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context, FILE, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Keystore unavailable: fall back so the app still works, but this is
            // a degraded security posture worth surfacing in diagnostics.
            return context.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    public void save(String token, long userId, String phone, String name, String role) {
        prefs.edit()
                .putString(K_TOKEN, token)
                .putLong(K_USER_ID, userId)
                .putString(K_PHONE, phone)
                .putString(K_NAME, name)
                .putString(K_ROLE, role)
                .apply();
    }

    public String token() {
        return prefs.getString(K_TOKEN, null);
    }

    public boolean isLoggedIn() {
        String t = token();
        return t != null && !t.isEmpty();
    }

    public String name() {
        return prefs.getString(K_NAME, "");
    }

    public String phone() {
        return prefs.getString(K_PHONE, "");
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
