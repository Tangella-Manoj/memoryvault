package io.memoryvault.dto.vault;

import java.util.List;
import java.util.Map;

public record AnalyticsResponse(
        long totalItems,
        double averageImportanceScore,
        double intelligenceScore,
        Map<String, Long> savesByDayOfWeek,
        Map<String, Long> itemsByContentType,
        Map<String, Long> itemsByEmotionalContext,
        List<TagCount> topTags
) {
    public record TagCount(String tag, long count) {
    }
}
