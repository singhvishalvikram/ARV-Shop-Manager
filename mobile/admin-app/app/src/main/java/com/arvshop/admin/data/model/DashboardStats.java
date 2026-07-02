package com.arvshop.admin.data.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Mirrors GET /dashboard data. */
public final class DashboardStats {

    public static final class TypeCount {
        public final String type;
        public final int count;
        public TypeCount(String type, int count) { this.type = type; this.count = count; }
    }

    public static final class LowStock {
        public final long id;
        public final String name;
        public final int quantity;
        public LowStock(long id, String name, int quantity) {
            this.id = id; this.name = name; this.quantity = quantity;
        }
    }

    public final int totalItems;
    public final int totalQuantity;
    public final double stockValue;
    public final double stockCost;
    public final double stockMrp;
    public final double todayRevenue;
    public final List<TypeCount> typeBreakdown;
    public final List<LowStock> recentItems;

    public DashboardStats(int totalItems, int totalQuantity, double stockValue,
                          double stockCost, double stockMrp, double todayRevenue,
                          List<TypeCount> typeBreakdown, List<LowStock> recentItems) {
        this.totalItems = totalItems;
        this.totalQuantity = totalQuantity;
        this.stockValue = stockValue;
        this.stockCost = stockCost;
        this.stockMrp = stockMrp;
        this.todayRevenue = todayRevenue;
        this.typeBreakdown = typeBreakdown;
        this.recentItems = recentItems;
    }

    public static DashboardStats fromJson(JSONObject o) {
        List<TypeCount> types = new ArrayList<>();
        JSONArray tb = o.optJSONArray("type_breakdown");
        if (tb != null) {
            for (int i = 0; i < tb.length(); i++) {
                JSONObject t = tb.optJSONObject(i);
                if (t != null) types.add(new TypeCount(t.optString("type", ""), t.optInt("count", 0)));
            }
        }
        List<LowStock> low = new ArrayList<>();
        JSONArray ri = o.optJSONArray("recent_items");
        if (ri != null) {
            for (int i = 0; i < ri.length(); i++) {
                JSONObject r = ri.optJSONObject(i);
                if (r != null) low.add(new LowStock(r.optLong("id", -1),
                        r.optString("name", ""), r.optInt("quantity", 0)));
            }
        }
        return new DashboardStats(
                o.optInt("total_items", 0),
                o.optInt("total_quantity", 0),
                o.optDouble("total_stock_value", 0),
                o.optDouble("total_stock_cost", 0),
                o.optDouble("total_stock_mrp", 0),
                o.optDouble("today_revenue", 0),
                types, low);
    }
}
