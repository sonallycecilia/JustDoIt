package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportJobServiceTest {

    @Mock private ExportJobRepository repository;
    @Mock private TaskExportPageReader pageReader;
    @Mock private TaskExportWorker worker;
    @Mock private ExportMetrics metrics;
    @Mock private ExportJobLinks links;
    @Mock private ExportFileStorage storage;
    @Mock private TemporaryDownloadTokenService tokens;
    private ExportProperties properties;
    private ExportJobService service;
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        properties = new ExportProperties();
        properties.setMaxActivePerUser(1);
        properties.setMaxRecords(1000);
        service = new ExportJobService(repository, pageReader, worker, properties,
                metrics, links, storage, tokens);
    }

    @Test
    @DisplayName("aceite persiste job e apenas despacha o worker")
    void requestPersistsAndDispatches() {
        UUID jobId = UUID.randomUUID();
        when(repository.countByUserIdAndStatusIn(any(), anyList())).thenReturn(0L);
        when(pageReader.count(USER_ID)).thenReturn(10L);
        when(repository.save(any())).thenAnswer(invocation -> {
            ExportJob job = invocation.getArgument(0);
            job.setId(jobId);
            job.setCreatedAt(LocalDateTime.now());
            return job;
        });
        when(links.statusPath(any())).thenReturn("/me/exports/" + jobId);

        ExportJobAcceptedResponse accepted = service.request(USER_ID, ExportFormat.JSON);

        assertThat(accepted.jobId()).isEqualTo(jobId);
        assertThat(accepted.status()).isEqualTo(ExportJobStatus.PENDING);
        verify(worker).process(jobId);
        verify(metrics).accepted();
    }

    @Test
    @DisplayName("segundo job ativo do mesmo usuário recebe 429")
    void activePerUserLimitIsEnforced() {
        when(repository.countByUserIdAndStatusIn(any(), anyList())).thenReturn(1L);

        assertThatThrownBy(() -> service.request(USER_ID, ExportFormat.CSV))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(429));
        verify(worker, never()).process(any());
        verify(metrics).rejected();
    }

    @Test
    @DisplayName("volume acima do teto recebe 413 antes de entrar na fila")
    void recordLimitIsEnforcedBeforeDispatch() {
        when(repository.countByUserIdAndStatusIn(any(), anyList())).thenReturn(0L);
        when(pageReader.count(USER_ID)).thenReturn(1001L);

        assertThatThrownBy(() -> service.request(USER_ID, ExportFormat.JSON))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value())
                        .isEqualTo(413));
        verify(worker, never()).process(any());
    }
}
