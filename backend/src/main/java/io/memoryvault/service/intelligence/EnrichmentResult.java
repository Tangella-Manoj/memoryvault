package io.memoryvault.service.intelligence;

import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.LifeContext;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plain holder for everything the intelligence pipeline computes for one URL,
 * kept independent of any managed JPA entity so the slow network work (Jsoup fetch,
 * AI service call) never happens while a {@code VaultItem} is loaded in a persistence context —
 * that gap is what previously let concurrent view-count updates get clobbered by a stale save.
 */
public record EnrichmentResult(
        String title,
        String summary,
        String ogImageUrl,
        ContentType contentType,
        EmotionalContext emotionalContext,
        LifeContext lifeContext,
        BigDecimal importanceScore,
        List<String> tagNames,
        String embeddingJson
) {
}
