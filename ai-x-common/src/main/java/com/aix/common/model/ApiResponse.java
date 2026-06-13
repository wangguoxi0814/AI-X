package com.aix.common.model;

public record ApiResponse<T>(
        ApiCode code,
        T data,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ApiCode.OK, data, null);
    }

    public static <T> ApiResponse<T> of(ApiCode code, String message) {
        return new ApiResponse<>(code, null, message);
    }
}
