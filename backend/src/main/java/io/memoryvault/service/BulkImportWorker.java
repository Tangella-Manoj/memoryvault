package io.memoryvault.service;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.enums.ImportJobStatus;
import io.memoryvault.repository.ImportJobRepository;
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
 * {@link ImportService#startImport} actually go through the proxy. Per-item persistence is
 * further delegated to {@link VaultItemImportPersister} for the same reason at the
 * {@code @Transactional} level — see that class's Javadoc.
 */
@Component
public class BulkImportWorker {

    private static final int BATCH_SIZE = 20;

    private final ImportJobRepository importJobRepository;
    private final VaultItemImportPersister persister;

    public BulkImportWorker(ImportJobRepository importJobRepository, VaultItemImportPersister persister) {
        this.importJobRepository = importJobRepository;
        this.persister = persister;
    }

    /**
     * Creates a {@code PROCESSING} vault item for each URL (in batches of {@value #BATCH_SIZE})
     * via {@link VaultItemImportPersister}, which publishes the saved event that content
     * enrichment listens for, then marks the job {@code DONE}. Runs on the
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

    void processOne(Long jobId, Long userId, String url) {
        try {
            persister.processOne(userId, url);
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
