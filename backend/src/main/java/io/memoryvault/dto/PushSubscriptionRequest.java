package io.memoryvault.dto;

import jakarta.validation.constraints.NotBlank;

/** Raw JSON of the browser's {@code PushSubscription.toJSON()} — stored as-is. */
public record PushSubscriptionRequest(
        @NotBlank String subscription
) {
}
