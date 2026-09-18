package io.memoryvault.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(
        String status,
        T data,
        String message,
        String errorCode,
        Instant timestamp,
        String requestId
) {
    public ApiResponse(String status, T data, String message, Instant timestamp, String requestId) {
        this(status, data, message, null, timestamp, requestId);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("SUCCESS", data, message, null, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("ERROR", null, message, null, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>("ERROR", null, message, errorCode, Instant.now(), UUID.randomUUID().toString());
    }
}
