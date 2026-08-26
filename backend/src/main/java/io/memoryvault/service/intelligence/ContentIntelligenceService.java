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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    @Async("intelligenceExecutor")
    public void enrich(Long vaultItemId) {
        Optional<VaultItem> maybeItem = vaultItemRepository.findById(vaultItemId);
        if (maybeItem.isEmpty()) {
            return;
        }
        VaultItem item = maybeItem.get();

        try {
            OpenGraphMetadata metadata = fetchWithDeadline(item.getUrl());
            applyEnrichment(item, metadata);
            item.setStatus(ItemStatus.PROCESSED);
        } catch (Exception ex) {
            log.warn("Failed to enrich vault item {}: {}", vaultItemId, ex.getMessage());
            item.setStatus(ItemStatus.FAILED);
        }

        save(item);
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

    @Transactional
    void applyEnrichment(VaultItem item, OpenGraphMetadata metadata) {
        if (item.getTitle() == null && metadata.title() != null) {
            item.setTitle(truncate(metadata.title(), 500));
        }
        item.setOgImageUrl(metadata.imageUrl());

        ContentType contentType = contentTypeDetector.detect(item.getUrl(), metadata);
        item.setContentType(contentType);

        String combinedText = String.join(" ",
                nullToEmpty(metadata.title()), nullToEmpty(metadata.description()), nullToEmpty(metadata.bodyText()));
        item.setEmotionalContext(contextKeywordDetector.detectEmotional(combinedText));
        item.setLifeContext(contextKeywordDetector.detectLife(combinedText));

        List<String> tagNames = List.of();
        Optional<ClaudeSummaryResult> claudeResult = claudeClient.summarizeAndTag(
                metadata.title(), metadata.description(), metadata.bodyText());

        if (claudeResult.isPresent()) {
            item.setSummary(claudeResult.get().summary());
            tagNames = claudeResult.get().tags();
        } else if (metadata.description() != null) {
            item.setSummary(truncate(metadata.description(), 500));
        }

        for (String tagName : tagNames) {
            if (tagName == null || tagName.isBlank()) {
                continue;
            }
            String normalized = tagName.trim().toLowerCase();
            Tag tag = tagRepository.findByName(normalized)
                    .orElseGet(() -> tagRepository.save(Tag.builder().name(normalized).build()));
            item.getTags().add(tag);
        }

        item.setImportanceScore(computeInitialImportance(item, tagNames.size()));
    }

    private BigDecimal computeInitialImportance(VaultItem item, int tagCount) {
        double score = 0.5;
        if (item.getOgImageUrl() != null) {
            score += 0.1;
        }
        if (item.getSummary() != null) {
            score += 0.1;
        }
        if (tagCount >= 3) {
            score += 0.1;
        }
        score = Math.min(score, 1.0);
        return BigDecimal.valueOf(score).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    @Transactional
    void save(VaultItem item) {
        vaultItemRepository.save(item);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
