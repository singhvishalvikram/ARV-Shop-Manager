package com.arvshop.admin.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Durable queue of item-creation requests made while offline. Each entry carries a
 * stable Idempotency-Key so that flushing after reconnect can retry safely without
 * ever creating a duplicate — the V01 backend deduplicates on this key
 * (items.create idempotency table). Pure JSON in SharedPreferences.
 */
public final class OfflineQueue {

    public static final class Pending {
        public final String idempotencyKey;
        public final JSONObject payload;
        Pending(String idempotencyKey, JSONObject payload) {
            this.idempotencyKey = idempotencyKey;
            this.payload = payload;
        }
    }

    private static final String PREFS = "arv_admin_queue";
    private static final String KEY = "pending_items";

    private final SharedPreferences prefs;

    public OfflineQueue(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Enqueue a new item create; returns the idempotency key assigned. */
    public synchronized String enqueueItemCreate(JSONObject payload) {
        String key = UUID.randomUUID().toString();
        List<Pending> all = load();
        try {
            JSONObject entry = new JSONObject();
            entry.put("key", key);
            entry.put("payload", payload);
            all.add(new Pending(key, payload));
        } catch (JSONException ignored) {
            return key;
        }
        persist(all);
        return key;
    }

    public synchronized List<Pending> load() {
        List<Pending> out = new ArrayList<>();
        String json = prefs.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject e = arr.optJSONObject(i);
                if (e == null) continue;
                String key = e.optString("key", "");
                JSONObject payload = e.optJSONObject("payload");
                if (!key.isEmpty() && payload != null) {
                    out.add(new Pending(key, payload));
                }
            }
        } catch (JSONException e) {
            prefs.edit().remove(KEY).apply();
        }
        return out;
    }

    public synchronized void remove(String idempotencyKey) {
        List<Pending> all = load();
        List<Pending> kept = new ArrayList<>();
        for (Pending p : all) {
            if (!p.idempotencyKey.equals(idempotencyKey)) kept.add(p);
        }
        persist(kept);
    }

    public synchronized int size() {
        return load().size();
    }

    private void persist(List<Pending> items) {
        JSONArray arr = new JSONArray();
        for (Pending p : items) {
            try {
                JSONObject e = new JSONObject();
                e.put("key", p.idempotencyKey);
                e.put("payload", p.payload);
                arr.put(e);
            } catch (JSONException ignored) {
                // skip un-serializable entry
            }
        }
        prefs.edit().putString(KEY, arr.toString()).apply();
    }
}
