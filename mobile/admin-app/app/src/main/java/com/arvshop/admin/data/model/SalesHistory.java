package com.arvshop.admin.data.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** GET /sales response: recent sales + rolled-up revenue. */
public final class SalesHistory {

    public final List<Sale> sales;
    public final double totalRevenue;
    public final int count;

    public SalesHistory(List<Sale> sales, double totalRevenue, int count) {
        this.sales = sales;
        this.totalRevenue = totalRevenue;
        this.count = count;
    }

    public static SalesHistory fromJson(JSONObject o) {
        List<Sale> list = new ArrayList<>();
        JSONArray arr = o.optJSONArray("sales");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject s = arr.optJSONObject(i);
                if (s != null) list.add(Sale.fromJson(s));
            }
        }
        return new SalesHistory(list, o.optDouble("total_revenue", 0), o.optInt("count", 0));
    }
}
