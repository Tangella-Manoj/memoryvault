package io.memoryvault.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email
) {
    public ForgotPasswordRequest {
        email = email != null ? email.trim() : null;
    }
}
