package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integração de ponta a ponta do /tasks/report com contexto completo e H2:
 * valida o boot da aplicação (beans novos: NotificationClient, OverdueTaskJob,
 * scheduling), as queries derivadas dos repositórios e o corte por usuário.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskReportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TaskRepository taskRepository;
    @Autowired private FocusSessionRepository focusSessionRepository;

    // Mesmo segredo do application-test.yml; token no formato exato do auth-service.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    private static final LocalDate SEG = LocalDate.of(2026, 6, 29);
    private static final LocalDate DOM = SEG.plusDays(6);

    private String tokenPara(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer("justdoit-auth-service")
                .audience().add("justdoit-api").and()
                .claim("email", "user@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(key)
                .compact();
    }

    private Task salvarTarefa(UUID userId, TaskStatus status, LocalDate dueDate) {
        return taskRepository.save(Task.builder()
                .userId(userId).title("Tarefa").status(status).dueDate(dueDate).build());
    }

    @Test
    @DisplayName("report agrega dados reais do banco, só do usuário do token")
    void report_agregaDadosDoBanco_porUsuario() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        // Alice: 2 tarefas com prazo na semana; 1 concluída na terça; 1h de foco na terça.
        salvarTarefa(alice, TaskStatus.PENDING, SEG.plusDays(4));
        Task concluida = salvarTarefa(alice, TaskStatus.COMPLETED, SEG.plusDays(1));
        concluida.setCompletedAt(SEG.plusDays(1).atTime(15, 0));
        taskRepository.save(concluida);
        focusSessionRepository.save(FocusSession.builder()
                .task(concluida)
                .startedAt(SEG.plusDays(1).atTime(14, 0))
                .endedAt(SEG.plusDays(1).atTime(15, 0))
                .completed(true)
                .build());

        // Bob: dados na mesma semana que NÃO podem vazar para o report da Alice.
        Task deBob = salvarTarefa(bob, TaskStatus.COMPLETED, SEG);
        deBob.setCompletedAt(SEG.atTime(10, 0));
        taskRepository.save(deBob);

        mockMvc.perform(get("/tasks/report")
                        .param("from", SEG.toString())
                        .param("to", DOM.toString())
                        .header("Authorization", "Bearer " + tokenPara(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.totalActualSeconds").value(3600))
                .andExpect(jsonPath("$.byDay.length()").value(7))
                .andExpect(jsonPath("$.byDay[1].date").value(SEG.plusDays(1).toString()))
                .andExpect(jsonPath("$.byDay[1].actualSeconds").value(3600))
                .andExpect(jsonPath("$.byDay[1].completedTasks").value(1))
                .andExpect(jsonPath("$.byDay[0].completedTasks").value(0));
    }

    @Test
    @DisplayName("PATCH /tasks/{id}/complete grava completedAt e reflete no report")
    void completar_gravaCompletedAt_eEntraNoReport() throws Exception {
        UUID userId = UUID.randomUUID();
        Task task = salvarTarefa(userId, TaskStatus.PENDING, LocalDate.now());
        String auth = "Bearer " + tokenPara(userId);

        // A notificação de conclusão é best-effort: o notification-service não está
        // de pé no teste e mesmo assim a operação deve responder 200.
        mockMvc.perform(patch("/tasks/{id}/complete", task.getId()).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getCompletedAt()).isNotNull();

        mockMvc.perform(get("/tasks/report")
                        .param("from", LocalDate.now().toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedTasks").value(1));
    }

    @Test
    @DisplayName("report sem token é bloqueado")
    void report_semToken_eBloqueado() throws Exception {
        mockMvc.perform(get("/tasks/report")
                        .param("from", SEG.toString())
                        .param("to", DOM.toString()))
                .andExpect(status().isForbidden());
    }
}
