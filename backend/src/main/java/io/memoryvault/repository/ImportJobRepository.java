package io.memoryvault.repository;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.enums.ImportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    @Transactional
    @Modifying
    @Query("""
        update ImportJob j
        set j.processed = j.processed + :processedDelta,
            j.failed = j.failed + :failedDelta
        where j.id = :jobId
    """)
    void incrementProgress(
            @Param("jobId") Long jobId,
            @Param("processedDelta") int processedDelta,
            @Param("failedDelta") int failedDelta
    );

    @Transactional
    @Modifying
    @Query("""
        update ImportJob j
        set j.status = :status,
            j.completedAt = :completedAt
        where j.id = :jobId
    """)
    void updateStatus(
            @Param("jobId") Long jobId,
            @Param("status") ImportJobStatus status,
            @Param("completedAt") Instant completedAt
    );
}
