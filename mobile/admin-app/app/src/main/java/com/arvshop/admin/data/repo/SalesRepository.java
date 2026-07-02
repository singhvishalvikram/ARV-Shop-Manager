package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.model.SalesHistory;
import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONException;
import org.json.JSONObject;

/** /api/v1/sales — record a sale (server decrements stock) and list history. */
public class SalesRepository {

    private final ApiClient api;

    public SalesRepository(ApiClient api) {
        this.api = api;
    }

    public SalesHistory list() throws ApiException {
        return SalesHistory.fromJson(api.getObject("/api/v1/sales"));
    }

    /**
     * @throws ApiException with code INSUFFICIENT_STOCK (HTTP 409) when qty exceeds stock.
     */
    public void recordSale(long itemId, int quantity, double price, String description)
            throws ApiException {
        JSONObject body = new JSONObject();
        try {
            body.put("item_id", itemId);
            body.put("quantity", quantity);
            body.put("price", price);
            body.put("description", description == null ? "" : description);
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Invalid sale input", 0);
        }
        api.postObject("/api/v1/sales", body);
    }
}
