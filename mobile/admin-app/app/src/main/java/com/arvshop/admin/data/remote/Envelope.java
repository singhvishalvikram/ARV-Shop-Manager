package com.arvshop.admin.data.remote;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Parses V01's universal response envelope:
 *   {"success":true,  "data":<T>, "error":null}
 *   {"success":false, "data":null, "error":{"code","message","details"}}
 * Pure logic — unit-tested in EnvelopeTest against fixtures captured from the live API.
 */
public final class Envelope {

    private Envelope() { }

    /**
     * @return the "data" object on success.
     * @throws ApiException carrying the backend error code + http status on failure.
     */
    public static Object unwrap(String body, int httpStatus) throws ApiException {
        JSONObject root;
        try {
            root = new JSONObject(body);
        } catch (JSONException e) {
            throw new ApiException(ApiException.CODE_PARSE,
                    "Unexpected server response", httpStatus);
        }
        boolean success = root.optBoolean("success", false);
        if (success && httpStatus >= 200 && httpStatus < 300) {
            return root.opt("data");
        }
        JSONObject error = root.optJSONObject("error");
        String code = error != null ? error.optString("code", "ERROR") : "ERROR";
        String message = error != null ? error.optString("message", "Request failed")
                : "Request failed (HTTP " + httpStatus + ")";
        throw new ApiException(code, message, httpStatus);
    }

    public static JSONObject dataObject(String body, int httpStatus) throws ApiException {
        Object data = unwrap(body, httpStatus);
        if (!(data instanceof JSONObject)) {
            throw new ApiException(ApiException.CODE_PARSE, "Expected an object", httpStatus);
        }
        return (JSONObject) data;
    }
}
