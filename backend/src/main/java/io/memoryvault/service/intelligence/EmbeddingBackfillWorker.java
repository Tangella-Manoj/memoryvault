package io.memoryvault.service.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.repository.VaultItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual per-item transactional work for {@link EmbeddingBackfillRunner}, split into
 * its own bean deliberately: Spring's proxy-based {@code @Transactional} only intercepts
 * calls that go through the bean's proxy, and a same-class call from the
 * {@code ApplicationRunner} would bypass it silently, leaving {@code item.getTags()}
 * un-fetchable outside a session (see this project's ADR #3 on cross-bean async/transactional
 * boundaries).
 */
@Service
class EmbeddingBackfillWorker {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillWorker.class);

    private final VaultItemRepository vaultItemRepository;
    private final AIService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    EmbeddingBackfillWorker(VaultItemRepository vaultItemRepository, AIService aiService) {
        this.vaultItemRepository = vaultItemRepository;
        this.aiService = aiService;
    }

    @Transactional
    void backfillOne(Long vaultItemId) {
        VaultItem item = vaultItemRepository.findById(vaultItemId).orElse(null);
        if (item == null || item.getEmbedding() != null) {
            return;
        }

        String text = String.join(" ",
                nullToEmpty(item.getTitle()),
                nullToEmpty(item.getSummary()),
                item.getTags().stream().map(t -> t.getName()).reduce("", (a, b) -> a + " " + b));

        aiService.generateEmbedding(text).ifPresent(vector -> {
            try {
                item.setEmbedding(objectMapper.writeValueAsString(vector));
                vaultItemRepository.save(item);
            } catch (Exception ex) {
                log.warn("Embedding backfill: failed to serialize embedding for item {}: {}", vaultItemId, ex.getMessage());
            }
        });
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
