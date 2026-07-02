package com.arvshop.admin.data.remote;

/**
 * Typed API failure carrying the backend error code (from the universal envelope)
 * and HTTP status, so callers can branch on e.g. UNAUTHORIZED (401) or
 * INSUFFICIENT_STOCK (409) without string-matching messages.
 */
public class ApiException extends Exception {

    public static final String CODE_NETWORK = "NETWORK";
    public static final String CODE_PARSE = "PARSE";

    public final String code;
    public final int httpStatus;

    public ApiException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public boolean isUnauthorized() {
        return httpStatus == 401;
    }
}
