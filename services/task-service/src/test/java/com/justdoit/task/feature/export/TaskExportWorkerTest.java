package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExportWorkerTest {

    @Mock private ExportJobRepository repository;
    @Mock private ExportFileStorage storage;
    @Mock private TaskExportStreamingWriter writer;
    @Mock private TaskExportService legacyExportService;
    @Mock private ExportProperties properties;
    @Mock private ExportMetrics metrics;
    @TempDir Path tempDir;

    private TaskExportWorker worker;
    private ExportJob job;
    private UUID jobId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        worker = new TaskExportWorker(repository, storage, writer, legacyExportService,
                properties, metrics);
        jobId = UUID.randomUUID();
        userId = UUID.randomUUID();
        job = ExportJob.builder().id(jobId).userId(userId).format(ExportFormat.CSV)
                .status(ExportJobStatus.PENDING).build();
        when(repository.findById(jobId)).thenReturn(Optional.of(job));
    }

    @Test
    void completedJobStoresMetadata() throws Exception {
        Path path = tempDir.resolve(jobId + ".csv");
        when(storage.allocate(jobId, ExportFormat.CSV))
                .thenReturn(new ExportFileStorage.StoredExport(jobId + ".csv", path));
        when(writer.write(userId, ExportFormat.CSV, path))
                .thenReturn(new ExportGenerationResult(500, 4096));
        when(properties.getDownloadTtl()).thenReturn(Duration.ofMinutes(15));
        when(legacyExportService.fileName(eq(ExportFormat.CSV), any()))
                .thenReturn("export_tarefas.csv");

        worker.process(jobId);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.COMPLETED);
        assertThat(job.getRecordCount()).isEqualTo(500);
        assertThat(job.getFileSizeBytes()).isEqualTo(4096);
        assertThat(job.getDownloadExpiresAt()).isEqualTo(job.getCompletedAt().plusMinutes(15));
        verify(metrics).completed(any(), eq(new ExportGenerationResult(500, 4096)));
    }

    @Test
    void timeoutMarksJobFailedAndDeletesPartialFile() throws Exception {
        Path path = tempDir.resolve(jobId + ".csv");
        when(storage.allocate(jobId, ExportFormat.CSV))
                .thenReturn(new ExportFileStorage.StoredExport(jobId + ".csv", path));
        when(writer.write(userId, ExportFormat.CSV, path))
                .thenThrow(new ExportTimeoutException("timeout controlado"));

        worker.process(jobId);

        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.FAILED);
        assertThat(job.getErrorMessage()).isEqualTo("timeout controlado");
        verify(storage).delete(jobId + ".csv");
        verify(metrics).failed(any(), eq("timeout"));
    }
}
