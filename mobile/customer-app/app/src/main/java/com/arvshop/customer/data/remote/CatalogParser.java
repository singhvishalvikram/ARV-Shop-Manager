package com.arvshop.customer.data.remote;

import com.arvshop.customer.data.model.Category;
import com.arvshop.customer.data.model.CatalogVersion;
import com.arvshop.customer.data.model.Product;
import com.arvshop.customer.data.model.ShopSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Parses the generated JSON from git-pages/data/. Defensive by design (Phase 12):
 * a single malformed product row is skipped, never crashing the whole catalog.
 * Pure Java — covered by CatalogParserTest against real production fixtures.
 */
public final class CatalogParser {

    private CatalogParser() { }

    /** Parses products.json. Rows missing id/name/price are skipped. */
    public static List<Product> parseProducts(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<Product> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            long id = o.optLong("id", -1);
            String name = o.optString("name", "").trim();
            double price = o.optDouble("price", -1);
            if (id < 0 || name.isEmpty() || price < 0) continue; // malformed row — skip
            String stockStatus = o.optString("stock_status", "");
            out.add(new Product(
                    id,
                    name,
                    o.optString("type", ""),
                    o.optString("description", ""),
                    price,
                    o.optDouble("mrp", price),
                    o.optInt("discount_percent", 0),
                    o.optString("image_url", ""),
                    o.optInt("featured", 0) == 1,
                    o.optString("badge", ""),
                    o.optInt("sort_order", 0),
                    !"out_of_stock".equalsIgnoreCase(stockStatus)));
        }
        return out;
    }

    public static List<Category> parseCategories(String json) throws JSONException {
        JSONArray arr = new JSONArray(json);
        List<Category> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            String name = o.optString("name", "").trim();
            if (name.isEmpty()) continue;
            out.add(new Category(name, o.optString("display_name", name),
                    o.optInt("sort_order", 0)));
        }
        return out;
    }

    /** settings.json is a flat map of strings. */
    public static ShopSettings parseSettings(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        Map<String, String> map = new HashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            map.put(k, o.optString(k, ""));
        }
        return new ShopSettings(map);
    }

    public static CatalogVersion parseVersion(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        return new CatalogVersion(
                o.optLong("v", CatalogVersion.NONE),
                o.optString("at", ""),
                o.optInt("count", 0));
    }
}
