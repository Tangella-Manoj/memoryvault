package io.memoryvault.service.intelligence;

import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.VaultItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time backfill for pre-existing {@code PROCESSED} vault items saved before this app
 * had an {@link AIService}-backed embedding column — runs once on startup, processing in
 * small batches with a short pause between them so it doesn't overwhelm a local Ollama
 * instance still warming up. Idempotent: items that already have an embedding are skipped,
 * so re-running (or a slow first run interrupted by a restart) just picks up where it left off.
 * The actual per-item transactional work lives in {@link EmbeddingBackfillWorker}, a
 * separate bean, so its {@code @Transactional} boundary isn't bypassed by same-class
 * self-invocation.
 */
@Component
@Profile({"dev", "prod"})
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);
    private static final int BATCH_SIZE = 10;
    private static final long DELAY_BETWEEN_BATCHES_MS = 500L;

    private final VaultItemRepository vaultItemRepository;
    private final EmbeddingBackfillWorker worker;

    public EmbeddingBackfillRunner(VaultItemRepository vaultItemRepository, EmbeddingBackfillWorker worker) {
        this.vaultItemRepository = vaultItemRepository;
        this.worker = worker;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<VaultItem> pending = vaultItemRepository.findByStatusAndEmbeddingIsNull(ItemStatus.PROCESSED);
        if (pending.isEmpty()) {
            log.info("Embedding backfill: nothing to do, all processed items already have an embedding");
            return;
        }

        List<Long> pendingIds = pending.stream().map(VaultItem::getId).toList();
        log.info("Embedding backfill: {} processed items missing an embedding, backfilling in batches of {}",
                pendingIds.size(), BATCH_SIZE);

        int done = 0;
        for (List<Long> batch : partition(pendingIds, BATCH_SIZE)) {
            for (Long id : batch) {
                worker.backfillOne(id);
            }
            done += batch.size();
            log.info("Embedding backfill: {}/{} done", done, pendingIds.size());
            sleep();
        }

        log.info("Embedding backfill complete: {} items processed", done);
    }

    private List<List<Long>> partition(List<Long> items, int size) {
        return java.util.stream.IntStream.range(0, (items.size() + size - 1) / size)
                .mapToObj(i -> items.subList(i * size, Math.min(items.size(), (i + 1) * size)))
                .toList();
    }

    private void sleep() {
        try {
            Thread.sleep(DELAY_BETWEEN_BATCHES_MS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
