package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONException;
import org.json.JSONObject;

/** POST /api/v1/sales — records a sale; server decrements stock atomically. */
public class SalesRepository {

    private final ApiClient api;

    public SalesRepository(ApiClient api) {
        this.api = api;
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
