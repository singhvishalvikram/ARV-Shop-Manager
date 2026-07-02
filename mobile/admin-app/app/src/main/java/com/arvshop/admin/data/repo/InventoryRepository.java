package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.local.OfflineQueue;
import com.arvshop.admin.data.model.Item;
import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Inventory CRUD against /api/v1/items, with offline-queued creation. */
public class InventoryRepository {

    private final ApiClient api;
    private final OfflineQueue queue;

    public InventoryRepository(ApiClient api, OfflineQueue queue) {
        this.api = api;
        this.queue = queue;
    }

    public List<Item> list(String search) throws ApiException {
        String path = "/api/v1/items";
        if (search != null && !search.trim().isEmpty()) {
            path += "?search=" + android.net.Uri.encode(search.trim());
        }
        JSONArray arr = api.getArray(path);
        List<Item> items = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            try {
                items.add(Item.fromJson(o));
            } catch (JSONException ignored) {
                // skip a single malformed row rather than fail the whole list
            }
        }
        return items;
    }

    public Item get(long id) throws ApiException, JSONException {
        return Item.fromJson(api.getObject("/api/v1/items/" + id));
    }

    /**
     * Create an item. On network failure the request is queued with an idempotency
     * key and {@code null} is returned to signal "saved offline, will sync".
     *
     * <p>The backend's {@code ItemCreate} schema intentionally does not accept
     * merchandising fields (visible/featured/badge) — those live on {@code ItemUpdate}.
     * So when the owner sets them on a NEW product, we create first, then apply the
     * merchandising with a follow-up PUT (verified against the live API). This keeps
     * "hide from customers" / "feature" correct at creation time rather than silently
     * dropping them.
     *
     * @param merchandising non-null only when it deviates from server defaults
     *                      (hidden, featured, or a badge); may be null.
     */
    public Item create(JSONObject payload, JSONObject merchandising) throws ApiException {
        String idemKey = java.util.UUID.randomUUID().toString();
        try {
            Item created = Item.fromJson(api.postObject("/api/v1/items", payload, idemKey));
            if (merchandising != null && merchandising.length() > 0) {
                return update(created.id, merchandising);
            }
            return created;
        } catch (ApiException e) {
            if (ApiException.CODE_NETWORK.equals(e.code)) {
                queue.enqueueItemCreate(payload);
                return null; // saved offline (merchandising applied after first sync + edit)
            }
            throw e;
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Malformed item response", 0);
        }
    }

    public Item update(long id, JSONObject payload) throws ApiException {
        try {
            return Item.fromJson(api.putObject("/api/v1/items/" + id, payload));
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Malformed item response", 0);
        }
    }

    public void delete(long id) throws ApiException {
        api.delete("/api/v1/items/" + id);
    }

    /** Flush queued offline creations; returns count successfully synced. */
    public int flushQueue() {
        int synced = 0;
        for (OfflineQueue.Pending p : queue.load()) {
            try {
                api.postObject("/api/v1/items", p.payload, p.idempotencyKey);
                queue.remove(p.idempotencyKey);
                synced++;
            } catch (ApiException e) {
                if (ApiException.CODE_NETWORK.equals(e.code)) break; // still offline; stop
                // Non-network error (e.g. validation) — drop it so it can't wedge the queue.
                queue.remove(p.idempotencyKey);
            }
        }
        return synced;
    }

    public int pendingCount() {
        return queue.size();
    }
}
