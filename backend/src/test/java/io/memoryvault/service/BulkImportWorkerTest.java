package io.memoryvault.service;

import io.memoryvault.domain.enums.ImportJobStatus;
import io.memoryvault.repository.ImportJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkImportWorkerTest {

    @Mock
    private ImportJobRepository importJobRepository;

    @Mock
    private VaultItemImportPersister persister;

    private BulkImportWorker worker;

    @BeforeEach
    void setUp() {
        worker = new BulkImportWorker(importJobRepository, persister);
    }

    @Test
    void processAsync_successfulBatch_updatesProgressAndMarksDone() {
        List<String> urls = List.of("https://a.com", "https://b.com", "https://c.com");
        when(persister.processOne(eq(1L), anyString())).thenReturn(10L);

        worker.processAsync(99L, 1L, urls);

        verify(importJobRepository).updateStatus(99L, ImportJobStatus.RUNNING, null);
        verify(importJobRepository).incrementProgress(99L, 3, 0);
        verify(importJobRepository).updateStatus(eq(99L), eq(ImportJobStatus.DONE), any(Instant.class));
    }

    @Test
    void processAsync_partialFailures_tracksBothProcessedAndFailed() {
        List<String> urls = List.of("https://good.com", "https://bad.com");
        when(persister.processOne(1L, "https://good.com")).thenReturn(10L);
        when(persister.processOne(1L, "https://bad.com")).thenThrow(new RuntimeException("Network timeout"));

        worker.processAsync(99L, 1L, urls);

        verify(importJobRepository).incrementProgress(99L, 1, 1);
        verify(importJobRepository).updateStatus(eq(99L), eq(ImportJobStatus.DONE), any(Instant.class));
    }

    @Test
    void processAsync_fatalException_marksJobFailed() {
        doThrow(new RuntimeException("DB offline")).when(importJobRepository).updateStatus(99L, ImportJobStatus.RUNNING, null);

        worker.processAsync(99L, 1L, List.of("https://a.com"));

        verify(importJobRepository).updateStatus(eq(99L), eq(ImportJobStatus.FAILED), any(Instant.class));
    }
}
