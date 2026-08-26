package io.memoryvault.dto.auth;

public record AuthResponse(
        Long userId,
        String email,
        String displayName,
        String accessToken,
        String refreshToken
) {
}
