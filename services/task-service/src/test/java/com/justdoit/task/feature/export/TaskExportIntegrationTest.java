package com.justdoit.task.feature.export;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskExportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskExportStreamingWriter writer;
    @MockitoBean private TaskExportWorker worker;
    @TempDir Path tempDir;

    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    @Test
    @DisplayName("API persiste job do dono do JWT e responde 202 sem executar carga na request")
    void request_persistsOwnedJobAndReturnsImmediately() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/me/exports").param("format", "json")
                        .header("Authorization", "Bearer " + tokenFor(userId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(worker).process(any(UUID.class));
    }

    @Test
    @DisplayName("streaming JSON e CSV nunca incluem tarefas de outro usuário")
    void streaming_isolatesUsers() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        saveTask(alice, "Tarefa da Alice");
        saveTask(bob, "Segredo do Bob");
        taskRepository.flush();

        Path json = tempDir.resolve("alice.json");
        Path csv = tempDir.resolve("alice.csv");
        ExportGenerationResult jsonResult = writer.write(alice, ExportFormat.JSON, json);
        ExportGenerationResult csvResult = writer.write(alice, ExportFormat.CSV, csv);

        String jsonText = Files.readString(json);
        String csvText = Files.readString(csv);
        assertThat(jsonResult.recordCount()).isEqualTo(1);
        assertThat(csvResult.recordCount()).isEqualTo(1);
        assertThat(jsonText).contains("Tarefa da Alice").doesNotContain("Segredo do Bob");
        assertThat(csvText).contains("Tarefa da Alice").doesNotContain("Segredo do Bob");
    }

    @Test
    @DisplayName("exportação sem token continua bloqueada")
    void request_withoutTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/me/exports").param("format", "json"))
                .andExpect(status().isForbidden());
    }

    private void saveTask(UUID userId, String title) {
        taskRepository.save(Task.builder()
                .userId(userId).title(title).status(TaskStatus.PENDING)
                .priority(Priority.NORMAL).dueDate(LocalDate.of(2026, 8, 20))
                .build());
    }

    private String tokenFor(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(userId.toString())
                .issuer("justdoit-auth-service").audience().add("justdoit-api").and()
                .claim("email", "user@test.com").claim("profile", "USER")
                .claim("type", "access").issuedAt(new Date(now))
                .expiration(new Date(now + 900_000)).signWith(key).compact();
    }
}
