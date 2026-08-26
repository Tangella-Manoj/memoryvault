package io.memoryvault.dto;

import java.time.Instant;

public record NotificationPreferenceResponse(
        boolean notificationsEnabled,
        boolean subscribed,
        int optimalHour,
        Instant lastCalculatedAt
) {
}
