package io.memoryvault.service;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ImportJobStatus;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.repository.ImportJobRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs the actual batch-processing loop for a bulk import job on the async executor.
 * Deliberately a separate bean from {@link ImportService}: Spring's {@code @Async} proxy
 * only intercepts calls made <em>through</em> the bean from another bean — a same-class
 * call (as {@code this.processAsync(...)}) bypasses the proxy and runs synchronously,
 * which previously made "async" bulk import block the HTTP request thread for the whole
 * batch. Splitting the async entry point into its own bean is what makes the call from
 * {@link ImportService#startImport} actually go through the proxy.
 */
@Component
public class BulkImportWorker {

    private static final int BATCH_SIZE = 20;

    private final ImportJobRepository importJobRepository;
    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BulkImportWorker(
            ImportJobRepository importJobRepository,
            VaultItemRepository vaultItemRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.importJobRepository = importJobRepository;
        this.vaultItemRepository = vaultItemRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Creates a {@code PROCESSING} vault item for each URL (in batches of {@value #BATCH_SIZE}),
     * publishing a {@link VaultItemSavedEvent} for each so content enrichment picks it up the
     * same way a normal save does, then marks the job {@code DONE}. Runs on the
     * {@code intelligenceExecutor} thread pool — never call this directly from within
     * {@link ImportService}; only cross-bean calls are actually asynchronous.
     *
     * @param jobId  the {@link ImportJob} to update progress on
     * @param userId the owning user
     * @param urls   URLs to import, already validated (max 500) by the controller
     */
    @Async("intelligenceExecutor")
    public void processAsync(Long jobId, Long userId, List<String> urls) {
        markRunning(jobId);

        for (int i = 0; i < urls.size(); i += BATCH_SIZE) {
            List<String> batch = urls.subList(i, Math.min(i + BATCH_SIZE, urls.size()));
            for (String url : batch) {
                processOne(jobId, userId, url);
            }
        }

        markDone(jobId);
    }

    @Transactional
    void markRunning(Long jobId) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ImportJobStatus.RUNNING);
            importJobRepository.save(job);
        });
    }

    @Transactional
    void processOne(Long jobId, Long userId, String url) {
        try {
            User user = userRepository.getReferenceById(userId);
            VaultItem item = VaultItem.builder()
                    .user(user)
                    .url(url)
                    .status(ItemStatus.PROCESSING)
                    .source(ItemSource.BULK_IMPORT)
                    .build();
            item = vaultItemRepository.save(item);
            eventPublisher.publishEvent(new VaultItemSavedEvent(item.getId()));

            incrementProcessed(jobId);
        } catch (Exception ex) {
            incrementFailed(jobId);
        }
    }

    @Transactional
    void incrementProcessed(Long jobId) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setProcessed(job.getProcessed() + 1);
            importJobRepository.save(job);
        });
    }

    @Transactional
    void incrementFailed(Long jobId) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setFailed(job.getFailed() + 1);
            importJobRepository.save(job);
        });
    }

    @Transactional
    void markDone(Long jobId) {
        importJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(ImportJobStatus.DONE);
            job.setCompletedAt(java.time.Instant.now());
            importJobRepository.save(job);
        });
    }
}
