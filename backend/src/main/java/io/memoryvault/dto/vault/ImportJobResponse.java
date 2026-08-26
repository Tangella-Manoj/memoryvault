package io.memoryvault.dto.vault;

import io.memoryvault.domain.ImportJob;
import io.memoryvault.domain.enums.ImportJobStatus;

public record ImportJobResponse(
        Long id,
        ImportJobStatus status,
        int total,
        int processed,
        int failed,
        int progressPercent
) {
    public static ImportJobResponse from(ImportJob job) {
        int percent = job.getTotal() == 0 ? 0 : (int) Math.round(100.0 * job.getProcessed() / job.getTotal());
        return new ImportJobResponse(job.getId(), job.getStatus(), job.getTotal(), job.getProcessed(), job.getFailed(), percent);
    }
}
