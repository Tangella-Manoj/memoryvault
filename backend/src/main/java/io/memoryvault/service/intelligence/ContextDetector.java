package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.Tag;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.EmotionalContext;
import io.memoryvault.domain.enums.LifeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cold start (fewer than 20 items): text/context similarity is unreliable signal at that
 * volume, so ranking falls back to recency + importance only.
 *
 * Otherwise, ranks by true semantic similarity: the query and each candidate item are
 * both embedded via {@link AIService#generateEmbedding}, and compared with cosine
 * similarity. An item that hasn't been embedded yet (or a query embedding failure) never
 * silently drops out — it falls back to the same keyword/Jaccard + emotional/life-context
 * scoring this class used before embeddings existed.
 */
@Service
public class ContextDetector {

    private static final Logger log = LoggerFactory.getLogger(ContextDetector.class);

    /** Below this many total items for a user, ranking falls back to the cold-start path. */
    public static final int COLD_START_THRESHOLD = 20;

    private final ContextKeywordDetector contextKeywordDetector;
    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param contextKeywordDetector used to classify both the query and each candidate
     *                               item's emotional/life context for the keyword fallback
     * @param aiService              supplies semantic embeddings for the query and items
     */
    public ContextDetector(ContextKeywordDetector contextKeywordDetector, AIService aiService) {
        this.contextKeywordDetector = contextKeywordDetector;
        this.aiService = aiService;
    }

    /**
     * Ranks candidate vault items against a free-text query. Below
     * {@link #COLD_START_THRESHOLD} total items for the user, falls back to a
     * recency+importance score instead of semantic similarity, since similarity signal is
     * unreliable at that volume; otherwise scores each candidate by cosine similarity
     * between the query's embedding and the item's stored embedding, falling back to
     * keyword/context matching for any item (or query) with no embedding available.
     *
     * @param query               free-text search query (may be empty, not null)
     * @param candidates          vault items to rank
     * @param totalUserItemCount  the user's total item count, used to decide cold-start
     * @return candidates sorted by descending score, each paired with its score and a
     *         short human-readable reason
     */
    public List<ScoredVaultItem> rank(String query, List<VaultItem> candidates, int totalUserItemCount) {
        boolean coldStart = totalUserItemCount < COLD_START_THRESHOLD;

        if (coldStart) {
            return candidates.stream()
                    .map(item -> new ScoredVaultItem(item, coldStartScore(item), "Recently saved, not yet enough history for smart matching"))
                    .sorted(Comparator.comparingDouble(ScoredVaultItem::score).reversed())
                    .collect(Collectors.toList());
        }

        Optional<List<Float>> queryEmbedding = aiService.generateEmbedding(query);

        EmotionalContext queryEmotional = contextKeywordDetector.detectEmotional(query);
        LifeContext queryLife = contextKeywordDetector.detectLife(query);
        Set<String> queryTokens = tokenize(query);

        return candidates.stream()
                .map(item -> scoreItem(item, queryEmbedding, queryTokens, queryEmotional, queryLife))
                .sorted(Comparator.comparingDouble(ScoredVaultItem::score).reversed())
                .collect(Collectors.toList());
    }

    private ScoredVaultItem scoreItem(VaultItem item, Optional<List<Float>> queryEmbedding,
                                       Set<String> queryTokens, EmotionalContext queryEmotional, LifeContext queryLife) {
        Optional<List<Float>> itemEmbedding = parseEmbedding(item);

        if (queryEmbedding.isPresent() && itemEmbedding.isPresent()) {
            double similarity = CosineSimilarity.of(queryEmbedding.get(), itemEmbedding.get());
            int percentage = (int) Math.round(similarity * 100);
            String reason = percentage + "% related to your search";
            return new ScoredVaultItem(item, similarity, reason);
        }

        return keywordFallbackScore(item, queryTokens, queryEmotional, queryLife);
    }

    private ScoredVaultItem keywordFallbackScore(VaultItem item, Set<String> queryTokens,
                                                  EmotionalContext queryEmotional, LifeContext queryLife) {
        double textScore = jaccardSimilarity(queryTokens, itemTokens(item)) * 0.4;

        double emotionalScore = item.getEmotionalContext() == queryEmotional ? 0.3 : 0.0;
        double lifeScore = item.getLifeContext() == queryLife ? 0.3 : 0.0;

        double score = textScore + emotionalScore + lifeScore;
        String reason = buildReason(textScore, emotionalScore, lifeScore, item);

        return new ScoredVaultItem(item, score, reason);
    }

    private Optional<List<Float>> parseEmbedding(VaultItem item) {
        if (item.getEmbedding() == null || item.getEmbedding().isBlank()) {
            return Optional.empty();
        }
        try {
            List<Float> vector = objectMapper.readerForListOf(Float.class).readValue(item.getEmbedding());
            return vector.isEmpty() ? Optional.empty() : Optional.of(vector);
        } catch (Exception ex) {
            log.warn("Failed to parse stored embedding for vault item {}: {}", item.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private double coldStartScore(VaultItem item) {
        double importance = item.getImportanceScore() != null ? item.getImportanceScore().doubleValue() : 0.0;
        long daysAgo = Duration.between(item.getSavedAt(), Instant.now()).toDays();
        double recency = Math.exp(-0.05 * daysAgo);
        return importance * 0.5 + recency * 0.5;
    }

    private String buildReason(double textScore, double emotionalScore, double lifeScore, VaultItem item) {
        if (emotionalScore > 0 && lifeScore > 0) {
            return "Matches your " + item.getEmotionalContext().name().toLowerCase() + " mood and " + item.getLifeContext().name().toLowerCase() + " focus";
        }
        if (lifeScore > 0) {
            return "Relevant to " + item.getLifeContext().name().toLowerCase();
        }
        if (emotionalScore > 0) {
            return "Matches your current " + item.getEmotionalContext().name().toLowerCase() + " mood";
        }
        if (textScore > 0) {
            return "Similar content to your search";
        }
        return "Weak match";
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private Set<String> itemTokens(VaultItem item) {
        StringBuilder sb = new StringBuilder();
        if (item.getTitle() != null) sb.append(item.getTitle()).append(' ');
        if (item.getSummary() != null) sb.append(item.getSummary()).append(' ');
        for (Tag tag : item.getTags()) {
            sb.append(tag.getName()).append(' ');
        }
        return tokenize(sb.toString());
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[^a-z0-9]+"))
                .filter(t -> t.length() > 2)
                .collect(Collectors.toSet());
    }
}
