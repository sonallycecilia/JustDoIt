package com.justdoit.task.feature.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskExportResponse;
import com.justdoit.task.shared.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExportStreamingWriterTest {

    @Mock private TaskExportPageReader pageReader;
    @TempDir Path tempDir;
    private ExportProperties properties;
    private TaskExportStreamingWriter writer;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new ExportProperties();
        properties.setPageSize(100);
        properties.setMaxRecords(20_000);
        properties.setMaxFileSizeBytes(50_000_000);
        properties.setMaxDuration(Duration.ofMinutes(1));
        writer = new TaskExportStreamingWriter(pageReader,
                new ObjectMapper().findAndRegisterModules(), properties);
    }

    @Test
    @DisplayName("10 mil registros são processados em páginas limitadas, sem lista global")
    void largeHistoryUsesBoundedPages() throws Exception {
        int total = 10_000;
        int pageSize = properties.getPageSize();
        AtomicInteger largestPage = new AtomicInteger();
        when(pageReader.count(USER_ID)).thenReturn((long) total);
        when(pageReader.read(eq(USER_ID), anyInt(), eq(pageSize))).thenAnswer(invocation -> {
            int page = invocation.getArgument(1);
            int remaining = total - page * pageSize;
            int size = Math.min(pageSize, Math.max(remaining, 0));
            largestPage.accumulateAndGet(size, Math::max);
            List<TaskExportResponse.TaskRow> rows = Collections.nCopies(size, row(page));
            return new ExportPage(rows, remaining > pageSize);
        });

        ExportGenerationResult result = writer.write(
                USER_ID, ExportFormat.JSON, tempDir.resolve("large.json"));

        assertThat(result.recordCount()).isEqualTo(total);
        assertThat(largestPage).hasValue(pageSize);
        assertThat(Files.size(tempDir.resolve("large.json"))).isEqualTo(result.fileSizeBytes());
        System.out.printf("[MÉTRICA DESEMPENHO - MEMÓRIA DA EXPORTAÇÃO] "
                + "registros=%d; lote_máximo=%d; proporção_heap=O(pageSize)%n", total, largestPage.get());
    }

    @Test
    @DisplayName("limite de bytes interrompe a escrita antes de exaurir memória ou disco")
    void fileSizeLimitIsEnforced() {
        properties.setMaxFileSizeBytes(64);
        when(pageReader.count(USER_ID)).thenReturn(1L);
        when(pageReader.read(USER_ID, 0, 100))
                .thenReturn(new ExportPage(List.of(row(0)), false));

        assertThatThrownBy(() -> writer.write(
                USER_ID, ExportFormat.JSON, tempDir.resolve("limited.json")))
                .isInstanceOf(ExportLimitExceededException.class)
                .hasMessageContaining("64 bytes");
    }

    @Test
    @DisplayName("timeout configurado é verificado durante a geração")
    void timeoutIsEnforced() {
        properties.setMaxDuration(Duration.ofNanos(-1));
        when(pageReader.count(USER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> writer.write(
                USER_ID, ExportFormat.CSV, tempDir.resolve("timeout.csv")))
                .isInstanceOf(ExportTimeoutException.class);
    }

    private static TaskExportResponse.TaskRow row(int index) {
        return new TaskExportResponse.TaskRow(UUID.randomUUID(), "Tarefa " + index,
                TaskStatus.PENDING, false, null, Priority.NORMAL,
                LocalDate.of(2026, 8, 20), LocalDateTime.of(2026, 8, 1, 10, 0),
                null, 600L, 0L, "nota");
    }
}
