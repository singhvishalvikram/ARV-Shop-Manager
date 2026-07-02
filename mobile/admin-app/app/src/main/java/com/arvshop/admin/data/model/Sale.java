package com.arvshop.admin.data.model;

import org.json.JSONObject;

/** One recorded sale from GET /sales (daily_sales joined with item name/type). */
public final class Sale {

    public final long id;
    public final long itemId;
    public final String itemName;
    public final String itemType;
    public final int quantitySold;
    public final double salePrice;
    public final String saleDate;
    public final String description;

    public Sale(long id, long itemId, String itemName, String itemType,
                int quantitySold, double salePrice, String saleDate, String description) {
        this.id = id;
        this.itemId = itemId;
        this.itemName = itemName;
        this.itemType = itemType;
        this.quantitySold = quantitySold;
        this.salePrice = salePrice;
        this.saleDate = saleDate;
        this.description = description;
    }

    public double lineTotal() {
        return quantitySold * salePrice;
    }

    public static Sale fromJson(JSONObject o) {
        return new Sale(
                o.optLong("id", -1),
                o.optLong("item_id", -1),
                o.optString("item_name", ""),
                o.optString("item_type", ""),
                o.optInt("quantity_sold", 0),
                o.optDouble("sale_price", 0),
                o.optString("sale_date", ""),
                o.optString("description", ""));
    }
}
