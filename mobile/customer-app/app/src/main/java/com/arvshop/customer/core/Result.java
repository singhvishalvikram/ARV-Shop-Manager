package com.arvshop.customer.core;

/**
 * Simple sealed-style result wrapper for LiveData: LOADING, SUCCESS(data) or ERROR(message).
 */
public final class Result<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;          // non-null when SUCCESS; may carry stale data on ERROR
    public final String message;  // non-null when ERROR

    private Result(Status status, T data, String message) {
        this.status = status;
        this.data = data;
        this.message = message;
    }

    public static <T> Result<T> loading() {
        return new Result<>(Status.LOADING, null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(Status.SUCCESS, data, null);
    }

    /** Error with optional stale data so the UI can keep showing the cached catalog. */
    public static <T> Result<T> error(String message, T staleData) {
        return new Result<>(Status.ERROR, staleData, message);
    }
}
