package io.memoryvault.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiResponse<T>(
        String status,
        T data,
        String message,
        Instant timestamp,
        String requestId
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>("SUCCESS", data, message, Instant.now(), UUID.randomUUID().toString());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>("ERROR", null, message, Instant.now(), UUID.randomUUID().toString());
    }
}
