package io.memoryvault.dto.plugin;

import java.time.Instant;

public record PluginTestResponse(
        boolean success,
        String message,
        Instant timestamp
) {
}
