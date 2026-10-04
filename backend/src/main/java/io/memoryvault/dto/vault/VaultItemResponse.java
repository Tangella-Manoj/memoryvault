package io.memoryvault.dto.vault;

import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.domain.enums.LifeContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record VaultItemResponse(
        Long id,
        String url,
        String title,
        String summary,
        String ogImageUrl,
        ContentType contentType,
        ItemSource source,
        ItemStatus status,
        EmotionalContext emotionalContext,
        LifeContext lifeContext,
        BigDecimal importanceScore,
        Instant savedAt,
        Instant lastSurfacedAt,
        Integer viewCount,
        List<String> tags
) {
    public static VaultItemResponse from(VaultItem item) {
        return new VaultItemResponse(
                item.getId(),
                item.getUrl(),
                item.getTitle(),
                item.getSummary(),
                item.getOgImageUrl(),
                item.getContentType(),
                item.getSource(),
                item.getStatus(),
                item.getEmotionalContext(),
                item.getLifeContext(),
                item.getImportanceScore(),
                item.getSavedAt(),
                item.getLastSurfacedAt(),
                item.getViewCount(),
                tagNames(item)
        );
    }

    private static List<String> tagNames(VaultItem item) {
        Set<io.memoryvault.domain.Tag> tags = item.getTags();
        if (tags == null) {
            return List.of();
        }
        return tags.stream().map(io.memoryvault.domain.Tag::getName).collect(Collectors.toList());
    }
}
