package com.justdoit.task.feature.export;

import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.category.CategoryRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.tasknote.TaskNote;
import com.justdoit.task.feature.tasknote.TaskNoteRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.feature.timer.TaskTimerRepository;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Exportação de ponta a ponta com contexto completo e H2. Cobre os critérios de
 * aceitação que dependem do banco: AC3 (o arquivo traz todos os campos essenciais)
 * e, principalmente, AC4 (isolamento — o usuário A jamais recebe tarefas do B).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TaskExportIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TaskRepository taskRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TaskTimerRepository timerRepository;
    @Autowired private TaskNoteRepository noteRepository;
    @PersistenceContext private EntityManager entityManager;

    // Mesmo segredo do application-test.yml; token no formato exato do auth-service.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

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

    private Category categoria(UUID userId, String nome) {
        return categoryRepository.save(Category.builder()
                .userId(userId).name(nome).color("#4f46e5").build());
    }

    private Task tarefa(UUID userId, String titulo, Category categoria) {
        return taskRepository.save(Task.builder()
                .userId(userId).title(titulo).category(categoria)
                .status(TaskStatus.PENDING).priority(Priority.NORMAL)
                .dueDate(LocalDate.of(2026, 7, 30))
                .build());
    }

    /**
     * O teste e o controller compartilham a mesma transação (e o mesmo contexto de
     * persistência). Sem limpar, o join fetch da exportação devolveria as instâncias
     * já em memória — com timer/nota ainda não associados — em vez de reler do banco.
     */
    private void sincronizarComOBanco() {
        entityManager.flush();
        entityManager.clear();
    }

    /* ─── AC4: isolamento ────────────────────────────────────────────────── */

    @Test
    @DisplayName("JSON traz só as tarefas do dono do token")
    void export_json_isolaPorUsuario() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        tarefa(alice, "Tarefa da Alice", null);
        tarefa(bob, "Segredo do Bob", categoria(bob, "Pessoal"));
        sincronizarComOBanco();

        String corpo = mockMvc.perform(get("/me/export").param("format", "json")
                        .header("Authorization", "Bearer " + tokenPara(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(alice.toString()))
                .andExpect(jsonPath("$.taskCount").value(1))
                .andExpect(jsonPath("$.tasks[0].title").value("Tarefa da Alice"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(corpo).doesNotContain("Segredo do Bob").doesNotContain(bob.toString());
    }

    @Test
    @DisplayName("CSV traz só as tarefas do dono do token")
    void export_csv_isolaPorUsuario() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        tarefa(alice, "Tarefa da Alice", null);
        tarefa(bob, "Segredo do Bob", null);
        sincronizarComOBanco();

        String csv = mockMvc.perform(get("/me/export").param("format", "csv")
                        .header("Authorization", "Bearer " + tokenPara(alice)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("Tarefa da Alice").doesNotContain("Segredo do Bob");
        assertThat(csv.split("\r\n")).hasSize(2); // cabeçalho + 1 tarefa
    }

    @Test
    @DisplayName("conta de outro usuário não vaza nem quando ele tem muito mais dados")
    void export_contaVazia_naoHerdaDadosAlheios() throws Exception {
        UUID semTarefas = UUID.randomUUID();
        UUID cheio = UUID.randomUUID();
        for (int i = 0; i < 3; i++) tarefa(cheio, "Tarefa " + i, null);
        sincronizarComOBanco();

        mockMvc.perform(get("/me/export").param("format", "json")
                        .header("Authorization", "Bearer " + tokenPara(semTarefas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskCount").value(0))
                .andExpect(jsonPath("$.tasks").isEmpty());

        String csv = mockMvc.perform(get("/me/export").param("format", "csv")
                        .header("Authorization", "Bearer " + tokenPara(semTarefas)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // Arquivo válido mesmo sem tarefas: só o cabeçalho.
        assertThat(csv.split("\r\n")).hasSize(1);
        assertThat(csv).contains("id,title,status");
    }

    /* ─── AC3: conteúdo ──────────────────────────────────────────────────── */

    @Test
    @DisplayName("arquivo traz nome, conclusão, categoria, datas, estimativa, cronômetro e nota")
    void export_trazCamposEssenciais() throws Exception {
        UUID userId = UUID.randomUUID();
        Category faculdade = categoria(userId, "Faculdade");
        Task task = tarefa(userId, "Entregar o TCC", faculdade);
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.of(2026, 7, 26, 18, 30));
        taskRepository.save(task);
        timerRepository.save(TaskTimer.builder().task(task).estimatedMinutes(90).actualSeconds(5400L).build());
        noteRepository.save(TaskNote.builder().task(task).content("Revisar a bibliografia").build());
        sincronizarComOBanco();

        mockMvc.perform(get("/me/export").param("format", "json")
                        .header("Authorization", "Bearer " + tokenPara(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].title").value("Entregar o TCC"))
                .andExpect(jsonPath("$.tasks[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.tasks[0].completed").value(true))
                .andExpect(jsonPath("$.tasks[0].categoryName").value("Faculdade"))
                .andExpect(jsonPath("$.tasks[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.tasks[0].completedAt").value("2026-07-26T18:30:00"))
                .andExpect(jsonPath("$.tasks[0].estimatedSeconds").value(5400))
                .andExpect(jsonPath("$.tasks[0].actualSeconds").value(5400))
                .andExpect(jsonPath("$.tasks[0].note").value("Revisar a bibliografia"));
    }

    @Test
    @DisplayName("CSV escapa nota com vírgula, aspas e quebra de linha sem virar registro novo")
    void export_csv_escapaNota() throws Exception {
        UUID userId = UUID.randomUUID();
        Task task = tarefa(userId, "Compras", null);
        noteRepository.save(TaskNote.builder().task(task)
                .content("pão, leite\nele disse \"depois\"").build());
        sincronizarComOBanco();

        String csv = mockMvc.perform(get("/me/export").param("format", "csv")
                        .header("Authorization", "Bearer " + tokenPara(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(csv).contains("\"pão, leite\nele disse \"\"depois\"\"\"");
        assertThat(csv.split("\r\n")).hasSize(2); // a quebra da nota está dentro das aspas
    }

    /* ─── Cabeçalhos e autorização ───────────────────────────────────────── */

    @Test
    @DisplayName("resposta vem como anexo com o nome carimbado pela data de hoje")
    void export_nomeDoArquivo() throws Exception {
        UUID userId = UUID.randomUUID();
        String auth = "Bearer " + tokenPara(userId);
        String hoje = LocalDate.now().toString();

        mockMvc.perform(get("/me/export").param("format", "csv").header("Authorization", auth))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"export_tarefas_" + hoje + ".csv\""));

        mockMvc.perform(get("/me/export").param("format", "json").header("Authorization", auth))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"export_tarefas_" + hoje + ".json\""));
    }

    @Test
    @DisplayName("exportação sem token é bloqueada")
    void export_semToken_eBloqueado() throws Exception {
        mockMvc.perform(get("/me/export").param("format", "json"))
                .andExpect(status().isForbidden());
    }
}
