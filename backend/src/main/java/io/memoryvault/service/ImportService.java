package io.memoryvault.service;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.User;
import io.memoryvault.domain.enums.ImportJobStatus;
import io.memoryvault.dto.vault.BulkImportRequest;
import io.memoryvault.dto.vault.ImportJobResponse;
import io.memoryvault.exception.ResourceNotFoundException;
import io.memoryvault.repository.ImportJobRepository;
import io.memoryvault.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for bulk URL imports. Creates the {@link ImportJob} record synchronously
 * and returns immediately (status {@code QUEUED}) — the actual batch processing runs on
 * {@link BulkImportWorker}, a separate bean so the {@code @Async} dispatch is real (see
 * that class's Javadoc for why it can't just be a method here).
 */
@Service
public class ImportService {

    private final ImportJobRepository importJobRepository;
    private final UserRepository userRepository;
    private final BulkImportWorker bulkImportWorker;

    public ImportService(
            ImportJobRepository importJobRepository,
            UserRepository userRepository,
            BulkImportWorker bulkImportWorker
    ) {
        this.importJobRepository = importJobRepository;
        this.userRepository = userRepository;
        this.bulkImportWorker = bulkImportWorker;
    }

    /**
     * Creates a queued import job for the given URLs and kicks off async processing.
     *
     * @param userId  the owning user
     * @param request validated list of up to 500 URLs to import
     * @return the newly created job, status {@code QUEUED} with 0 processed
     */
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

        bulkImportWorker.processAsync(job.getId(), userId, request.urls());

        return ImportJobResponse.from(job);
    }

    /**
     * @param userId the caller, used to enforce that jobs can only be polled by their owner
     * @param jobId  the job to check
     * @return current progress: status, total, processed, failed, and a computed percentage
     * @throws ResourceNotFoundException if no such job exists for this user
     */
    @Transactional(readOnly = true)
    public ImportJobResponse getStatus(Long userId, Long jobId) {
        ImportJob job = importJobRepository.findById(jobId)
                .filter(j -> j.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("ImportJob", jobId));
        return ImportJobResponse.from(job);
    }
}
