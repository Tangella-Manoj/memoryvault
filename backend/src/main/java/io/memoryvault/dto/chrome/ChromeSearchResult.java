package io.memoryvault.dto.chrome;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Search-result shape for the extension's overlay panel — deliberately flat and
 * snake_case (not the app's usual camelCase DTOs) to match the content script's
 * expected field names directly, no client-side remapping needed.
 */
public record ChromeSearchResult(
        String title,
        String summary,
        String url,
        String platform,
        @JsonProperty("days_since_saved") long daysSinceSaved,
        @JsonProperty("importance_score") BigDecimal importanceScore,
        @JsonProperty("resurface_reason") String resurfaceReason,
        @JsonProperty("context_score") double contextScore
) {
}
