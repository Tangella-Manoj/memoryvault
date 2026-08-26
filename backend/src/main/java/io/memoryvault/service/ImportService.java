package io.memoryvault.service;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.User;
import io.memoryvault.domain.VaultItem;
import io.memoryvault.domain.enums.ImportJobStatus;
import io.memoryvault.domain.enums.ItemSource;
import io.memoryvault.domain.enums.ItemStatus;
import io.memoryvault.dto.vault.BulkImportRequest;
import io.memoryvault.dto.vault.ImportJobResponse;
import io.memoryvault.exception.ResourceNotFoundException;
import io.memoryvault.repository.ImportJobRepository;
import io.memoryvault.repository.UserRepository;
import io.memoryvault.repository.VaultItemRepository;
import io.memoryvault.service.intelligence.VaultItemSavedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ImportService {

    private static final int BATCH_SIZE = 20;

    private final ImportJobRepository importJobRepository;
    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ImportService(
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

    @Transactional
    public ImportJobResponse startImport(Long userId, BulkImportRequest request) {
        User user = userRepository.getReferenceById(userId);

        ImportJob job = ImportJob.builder()
                .user(user)
                .status(ImportJobStatus.QUEUED)
                .total(request.urls().size())
                .processed(0)
                .failed(0)
                .build();
        job = importJobRepository.save(job);

        processAsync(job.getId(), userId, request.urls());

        return ImportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getStatus(Long userId, Long jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .filter(j -> j.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("ImportJob", jobId));
        return ImportJobResponse.from(job);
    }

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
