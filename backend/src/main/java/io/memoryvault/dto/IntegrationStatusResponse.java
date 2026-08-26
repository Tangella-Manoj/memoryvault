package io.memoryvault.dto;

import java.time.Instant;

public record IntegrationStatusResponse(
        boolean connected,
        boolean syncEnabled,
        Instant lastSyncedAt,
        long totalItemsSynced
) {
}
