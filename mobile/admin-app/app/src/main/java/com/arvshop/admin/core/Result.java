package com.arvshop.admin.core;

/** LOADING / SUCCESS(data) / ERROR(message,code) wrapper for LiveData. */
public final class Result<T> {

    public enum Status { LOADING, SUCCESS, ERROR }

    public final Status status;
    public final T data;
    public final String message;
    public final String code;

    private Result(Status status, T data, String message, String code) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.code = code;
    }

    public static <T> Result<T> loading() {
        return new Result<>(Status.LOADING, null, null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(Status.SUCCESS, data, null, null);
    }

    public static <T> Result<T> error(String message, String code) {
        return new Result<>(Status.ERROR, null, message, code);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
