package com.justdoit.task.feature.export;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExportEndpointIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskExportPageReader pageReader;
    @MockitoBean private TaskExportStreamingWriter writer;

    private final CountDownLatch workersStarted = new CountDownLatch(2);
    private final CountDownLatch releaseWorkers = new CountDownLatch(1);

    @AfterEach
    void releaseWorkers() {
        releaseWorkers.countDown();
    }

    @Test
    void saturatedExportPoolDoesNotBlockRegularApiEndpoint() throws Exception {
        when(pageReader.count(any())).thenReturn(1L);
        when(writer.write(any(), any(), any())).thenAnswer(invocation -> {
            workersStarted.countDown();
            if (!releaseWorkers.await(10, TimeUnit.SECONDS)) {
                throw new ExportTimeoutException("worker de teste não foi liberado");
            }
            return new ExportGenerationResult(1, 128);
        });

        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        mockMvc.perform(post("/me/exports").param("format", "json")
                        .with(authenticatedUser(firstUser)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/me/exports").param("format", "csv")
                        .with(authenticatedUser(secondUser)))
                .andExpect(status().isAccepted());
        assertThat(workersStarted.await(10, TimeUnit.SECONDS)).isTrue();

        long startedAt = System.nanoTime();
        mockMvc.perform(get("/tasks").with(authenticatedUser(UUID.randomUUID())))
                .andExpect(status().isOk());
        long elapsed = System.nanoTime() - startedAt;

        assertThat(elapsed).isLessThan(Duration.ofSeconds(2).toNanos());
        System.out.printf("[MÉTRICA DESEMPENHO - ISOLAMENTO DA EXPORTAÇÃO] "
                + "workers_ocupados=2; endpoint_regular_ms=%.2f; limite_ms=2000%n",
                elapsed / 1_000_000.0);
    }
}
