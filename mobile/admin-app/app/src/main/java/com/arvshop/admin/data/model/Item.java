package com.arvshop.admin.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Inventory item. Mirrors the live `_row_to_item` shape exactly (booleans arrive as
 * 1/0 integers). Owner-side, so cost/location ARE present here (unlike the customer
 * catalog). Parse defensively — never crash the list on one bad row.
 */
public final class Item {

    public final long id;
    public final String name;
    public final String type;
    public final String description;
    public final double price;
    public final double mrp;
    public final double purchaseCost;
    public final String location;
    public final int quantity;
    public final String imageUrl;
    public final boolean visible;
    public final boolean featured;
    public final String badge;
    public final int sortOrder;
    public final String titleOverride;
    public final String descriptionOverride;
    public final String stockStatus;

    public Item(long id, String name, String type, String description, double price,
                double mrp, double purchaseCost, String location, int quantity,
                String imageUrl, boolean visible, boolean featured, String badge,
                int sortOrder, String titleOverride, String descriptionOverride,
                String stockStatus) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.price = price;
        this.mrp = mrp;
        this.purchaseCost = purchaseCost;
        this.location = location;
        this.quantity = quantity;
        this.imageUrl = imageUrl;
        this.visible = visible;
        this.featured = featured;
        this.badge = badge;
        this.sortOrder = sortOrder;
        this.titleOverride = titleOverride;
        this.descriptionOverride = descriptionOverride;
        this.stockStatus = stockStatus;
    }

    public boolean isOutOfStock() {
        return "out_of_stock".equalsIgnoreCase(stockStatus) || quantity <= 0;
    }

    public static Item fromJson(JSONObject o) throws JSONException {
        if (o == null) throw new JSONException("null item");
        return new Item(
                o.optLong("id", -1),
                o.optString("name", ""),
                o.optString("type", ""),
                o.optString("description", ""),
                o.optDouble("price", 0),
                o.optDouble("mrp", 0),
                o.optDouble("purchase_cost", 0),
                o.optString("location", ""),
                o.optInt("quantity", 0),
                o.optString("image_url", ""),
                o.optInt("visible", 1) == 1,
                o.optInt("featured", 0) == 1,
                o.optString("badge", ""),
                o.optInt("sort_order", 0),
                o.optString("title_override", ""),
                o.optString("description_override", ""),
                o.optString("stock_status", "in_stock"));
    }
}
