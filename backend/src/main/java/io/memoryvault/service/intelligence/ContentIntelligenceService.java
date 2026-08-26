package io.memoryvault.service.intelligence;

import io.memoryvault.domain.Tag;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ContentType;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.TagRepository;
import io.memoryvault.repository.VaultItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enriches a freshly-saved {@link VaultItem} with metadata, an AI summary, tags, and
 * detected context — running the slow I/O (page fetch, Claude call) entirely outside
 * any JPA persistence context, then applying the result to a freshly-reloaded entity in
 * one short transaction. This ordering matters: earlier the same class held a managed
 * entity across the whole multi-second network round trip and blind-saved it at the end,
 * which silently clobbered concurrent updates (e.g. view-count increments) that landed
 * on the row while the fetch/Claude call was still in flight.
 */
@Service
public class ContentIntelligenceService {

    private static final Logger log = LoggerFactory.getLogger(ContentIntelligenceService.class);
    private static final long ENRICHMENT_DEADLINE_SECONDS = 20;

    private final ExecutorService fetchExecutor = Executors.newCachedThreadPool();

    private final VaultItemRepository vaultItemRepository;
    private final TagRepository tagRepository;
    private final OpenGraphExtractor openGraphExtractor;
    private final ContentTypeDetector contentTypeDetector;
    private final ContextKeywordDetector contextKeywordDetector;
    private final ClaudeIntelligenceClient claudeClient;

    public ContentIntelligenceService(
            VaultItemRepository vaultItemRepository,
            TagRepository tagRepository,
            OpenGraphExtractor openGraphExtractor,
            ContentTypeDetector contentTypeDetector,
            ContextKeywordDetector contextKeywordDetector,
            ClaudeIntelligenceClient claudeClient
    ) {
        this.vaultItemRepository = vaultItemRepository;
        this.tagRepository = tagRepository;
        this.openGraphExtractor = openGraphExtractor;
        this.contentTypeDetector = contentTypeDetector;
        this.contextKeywordDetector = contextKeywordDetector;
        this.claudeClient = claudeClient;
    }

    /**
     * Listens for a committed {@link VaultItemSavedEvent} and kicks off enrichment
     * on the async executor. Runs after commit so the item is guaranteed visible to
     * the enrichment read (see the Phase 2 race-condition fix this replaced).
     *
     * @param event carries the id of the vault item to enrich
     */
    @Async("intelligenceExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVaultItemSaved(VaultItemSavedEvent event) {
        enrich(event.vaultItemId());
    }

    /**
     * Enriches one vault item end to end: fetches the page, detects content type and
     * context, asks Claude for a summary and tags (falling back to the page's own
     * meta description if Claude is unavailable), computes an initial importance
     * score, and persists the result. Never throws — failures are recorded as a
     * {@code FAILED} status on the item itself.
     *
     * @param vaultItemId id of the previously-saved, still-{@code PROCESSING} item
     */
    public void enrich(Long vaultItemId) {
        String url = readUrl(vaultItemId);
        if (url == null) {
            log.warn("enrich() found no VaultItem for id={}", vaultItemId);
            return;
        }

        try {
            OpenGraphMetadata metadata = fetchWithDeadline(url);
            EnrichmentResult result = computeEnrichment(url, metadata);
            applyAndSave(vaultItemId, result, ItemStatus.PROCESSED);
        } catch (Exception ex) {
            log.warn("Failed to enrich vault item {}: {}", vaultItemId, ex.getMessage());
            markFailed(vaultItemId);
        }
    }

    @Transactional(readOnly = true)
    String readUrl(Long vaultItemId) {
        return vaultItemRepository.findById(vaultItemId).map(VaultItem::getUrl).orElse(null);
    }

    private OpenGraphMetadata fetchWithDeadline(String url) throws Exception {
        CompletableFuture<OpenGraphMetadata> future = CompletableFuture.supplyAsync(() -> {
            try {
                return openGraphExtractor.extract(url);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, fetchExecutor);

        try {
            return future.get(ENRICHMENT_DEADLINE_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new TimeoutException("Fetching " + url + " exceeded " + ENRICHMENT_DEADLINE_SECONDS + "s deadline");
        }
    }

    /**
     * Pure computation (no entity access): detects content type/context, calls Claude
     * for a summary and tags, and derives an initial importance score. Safe to run
     * outside any transaction since it never touches a managed {@link VaultItem}.
     *
     * @param url      the item's URL, used for content-type detection
     * @param metadata page metadata already extracted via {@link OpenGraphExtractor}
     * @return everything needed to update the vault item, independent of entity state
     */
    EnrichmentResult computeEnrichment(String url, OpenGraphMetadata metadata) {
        ContentType contentType = contentTypeDetector.detect(url, metadata);

        String combinedText = String.join(" ",
                nullToEmpty(metadata.title()), nullToEmpty(metadata.description()), nullToEmpty(metadata.bodyText()));
        var emotionalContext = contextKeywordDetector.detectEmotional(combinedText);
        var lifeContext = contextKeywordDetector.detectLife(combinedText);

        String summary = null;
        List<String> tagNames = List.of();
        Optional<ClaudeSummaryResult> claudeResult = claudeClient.summarizeAndTag(
                metadata.title(), metadata.description(), metadata.bodyText());

        if (claudeResult.isPresent()) {
            summary = claudeResult.get().summary();
            tagNames = claudeResult.get().tags();
        } else if (metadata.description() != null) {
            summary = truncate(metadata.description(), 500);
        }

        BigDecimal importanceScore = computeInitialImportance(metadata.imageUrl() != null, summary != null, tagNames.size());

        return new EnrichmentResult(
                metadata.title() != null ? truncate(metadata.title(), 500) : null,
                summary,
                metadata.imageUrl(),
                contentType,
                emotionalContext,
                lifeContext,
                importanceScore,
                tagNames
        );
    }

    /**
     * Reloads the vault item fresh and applies the computed enrichment in a single short
     * transaction, so the load-mutate-save window is milliseconds rather than the
     * multi-second network round trip that produced {@code result}. This is what keeps
     * concurrent writers (view tracking, rediscovery) from being overwritten.
     *
     * @param vaultItemId id of the item to update
     * @param result      previously computed enrichment data
     * @param status      terminal status to set (PROCESSED on success)
     */
    @Transactional
    void applyAndSave(Long vaultItemId, EnrichmentResult result, ItemStatus status) {
        VaultItem item = vaultItemRepository.findById(vaultItemId).orElse(null);
        if (item == null) {
            return;
        }

        if (item.getTitle() == null && result.title() != null) {
            item.setTitle(result.title());
        }
        item.setOgImageUrl(result.ogImageUrl());
        item.setContentType(result.contentType());
        item.setEmotionalContext(result.emotionalContext());
        item.setLifeContext(result.lifeContext());
        if (result.summary() != null) {
            item.setSummary(result.summary());
        }
        item.setImportanceScore(result.importanceScore());

        for (String tagName : result.tagNames()) {
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            String normalized = tagName.trim().toLowerCase();
            Tag tag = tagRepository.findByName(normalized)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(normalized).build()));
            item.getTags().add(tag);
        }

        item.setStatus(status);
        vaultItemRepository.save(item);
    }

    @Transactional
    void markFailed(Long vaultItemId) {
        vaultItemRepository.findById(vaultItemId).ifPresent(item -> {
            item.setStatus(ItemStatus.FAILED);
            vaultItemRepository.save(item);
        });
    }

    private BigDecimal computeInitialImportance(boolean hasImage, boolean hasSummary, int tagCount) {
        double score = 0.5;
        if (hasImage) {
            score += 0.1;
        }
        if (hasSummary) {
            score += 0.1;
        }
        if (tagCount >= 3) {
            score += 0.1;
        }
        score = Math.min(score, 1.0);
        return BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
