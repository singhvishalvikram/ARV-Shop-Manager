package com.arvshop.admin.data.repo;

import com.arvshop.admin.data.remote.ApiClient;
import com.arvshop.admin.data.remote.ApiException;

import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET/POST /api/v1/settings — white-label + display toggles. */
public class SettingsRepository {

    private final ApiClient api;

    public SettingsRepository(ApiClient api) {
        this.api = api;
    }

    public Map<String, String> load() throws ApiException {
        JSONObject o = api.getObject("/api/v1/settings");
        Map<String, String> map = new LinkedHashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            map.put(k, o.optString(k, ""));
        }
        return map;
    }

    public void save(Map<String, String> changed) throws ApiException {
        JSONObject body = new JSONObject();
        try {
            for (Map.Entry<String, String> e : changed.entrySet()) {
                body.put(e.getKey(), e.getValue());
            }
        } catch (org.json.JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE, "Invalid settings", 0);
        }
        api.postObject("/api/v1/settings", body);
    }
}
