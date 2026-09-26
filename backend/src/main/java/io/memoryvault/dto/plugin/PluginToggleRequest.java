package io.memoryvault.dto.plugin;

import java.util.Map;

public record PluginToggleRequest(
        boolean enabled,
        Map<String, String> settings
) {
}
