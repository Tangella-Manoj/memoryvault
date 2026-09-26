package io.memoryvault.dto.plugin;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PluginInfoResponse(
        String id,
        String name,
        String category,
        String description,
        String version,
        String author,
        boolean enabled,
        boolean configured,
        String icon,
        List<String> capabilities,
        Map<String, String> settings,
        Instant lastSyncedAt,
        Map<String, Object> stats
) {
}
