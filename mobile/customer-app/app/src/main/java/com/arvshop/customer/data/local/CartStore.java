package com.arvshop.customer.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.arvshop.customer.data.model.CartItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Cart persistence in SharedPreferences as a JSON array — the native analogue of
 * the PWA's localStorage cart. Local-only by design: no cart data leaves the device.
 */
public final class CartStore {

    private static final String PREFS = "cart_prefs";
    private static final String KEY_ITEMS = "items";

    private final SharedPreferences prefs;

    public CartStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<CartItem> load() {
        String json = prefs.getString(KEY_ITEMS, "[]");
        List<CartItem> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                int qty = o.optInt("qty", 0);
                long id = o.optLong("id", -1);
                if (id < 0 || qty <= 0) continue;
                out.add(new CartItem(id, o.optString("name", ""),
                        o.optDouble("price", 0), o.optString("imageUrl", ""), qty));
            }
        } catch (JSONException e) {
            // Corrupt cart — start clean rather than crash; nothing critical is lost.
            prefs.edit().remove(KEY_ITEMS).apply();
        }
        return out;
    }

    public synchronized void save(List<CartItem> items) {
        JSONArray arr = new JSONArray();
        try {
            for (CartItem it : items) {
                JSONObject o = new JSONObject();
                o.put("id", it.productId);
                o.put("name", it.name);
                o.put("price", it.price);
                o.put("imageUrl", it.imageUrl);
                o.put("qty", it.qty);
                arr.put(o);
            }
        } catch (JSONException e) {
            return; // values are primitives/strings — cannot realistically happen
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
    }
}
