package io.memoryvault.dto;

import io.memoryvault.domain.User;

public record UserProfileResponse(
        Long id,
        String email,
        String displayName,
        String vaultEmail,
        String timezone
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getVaultEmail(), user.getTimezone());
    }
}
