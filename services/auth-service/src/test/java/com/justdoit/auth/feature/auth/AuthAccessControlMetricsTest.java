package com.justdoit.auth.feature.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.auth.shared.RegisterRequest;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Atributo de Qualidade: SEGURANÇA
 * Métrica: Taxa de Bloqueio de Acesso Não Autorizado
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de requisições com token JWT ausente, inválido ou de outro usuário
 *       que foram CORRETAMENTE REJEITADAS.
 *   B = nº total de requisições inválidas disparadas.
 *
 * Qualquer valor abaixo de 1 indica uma brecha crítica na proteção das rotas,
 * permitindo vazamento de dados. Este teste dispara uma bateria de requisições
 * inválidas contra TODAS as rotas protegidas e exige X == 1.0.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthAccessControlMetricsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Mesmo segredo do application-test.yml — usado para forjar tokens válidos/expirados.
    private static final String TEST_SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";
    // Segredo DIFERENTE — produz tokens com assinatura inválida.
    private static final String WRONG_SECRET =
            "another-totally-different-secret-key-256-bits-aaaaaaaaaaaaaaaaaaa";

    /** Rotas que EXIGEM autenticação (qualquer rota fora das permitAll do WebSecurityConfig). */
    private List<RequestBuilder> protectedEndpoints(String authHeaderValue) {
        Function<RequestBuilder, RequestBuilder> withAuth = rb ->
                authHeaderValue == null ? rb : applyHeader(rb, authHeaderValue);

        List<RequestBuilder> endpoints = new ArrayList<>();
        endpoints.add(withAuth.apply(get("/auth/me")));
        endpoints.add(withAuth.apply(post("/auth/logout")));
        endpoints.add(withAuth.apply(put("/auth/me")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")));
        endpoints.add(withAuth.apply(delete("/auth/me")));
        return endpoints;
    }

    private RequestBuilder applyHeader(RequestBuilder rb, String value) {
        // Todos os builders aqui são MockHttpServletRequestBuilder.
        return ((org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder) rb)
                .header("Authorization", value);
    }

    // ─────────────────────────────────────────────
    // Geração de tokens forjados
    // ─────────────────────────────────────────────

    private String tokenSignedWith(String secret, long expirationOffsetMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "intruso@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + expirationOffsetMs))
                .signWith(key)
                .compact();
    }

    /** Lista dos cabeçalhos "Authorization" inválidos que um atacante poderia enviar. */
    private List<String> invalidAuthHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("");                                              // vazio
        headers.add("Bearer ");                                       // bearer sem token
        headers.add("Bearer not-a-jwt-at-all");                       // lixo
        headers.add("Bearer " + tokenSignedWith(WRONG_SECRET, 900_000));   // assinatura inválida
        headers.add("Bearer " + tokenSignedWith(TEST_SECRET, -60_000));    // expirado
        headers.add("Basic dXNlcjpwYXNzd29yZA==");                    // esquema errado
        headers.add(tokenSignedWith(TEST_SECRET, 900_000));           // token sem prefixo "Bearer"
        headers.add("Bearer " + tamper(tokenSignedWith(TEST_SECRET, 900_000))); // token adulterado
        return headers;
    }

    /** Adultera o último caractere da assinatura, invalidando o token. */
    private String tamper(String token) {
        char last = token.charAt(token.length() - 1);
        char replacement = last == 'A' ? 'B' : 'A';
        return token.substring(0, token.length() - 1) + replacement;
    }

    private boolean isBlocked(int status) {
        // 401 (não autenticado) e 403 (proibido) são respostas de bloqueio corretas.
        return status == 401 || status == 403;
    }

    // ─────────────────────────────────────────────
    // MÉTRICA: X = A / B  (deve ser exatamente 1.0)
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("métrica de segurança: taxa de bloqueio de acesso não autorizado deve ser 1.0")
    void taxaDeBloqueioDeAcessoNaoAutorizado_deveSer1() throws Exception {
        int totalRequisicoesInvalidas = 0;   // B
        int totalCorretamenteRejeitadas = 0; // A
        List<String> falhas = new ArrayList<>();

        // 1) Requisições SEM token (Authorization ausente).
        for (RequestBuilder request : protectedEndpoints(null)) {
            totalRequisicoesInvalidas++;
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            if (isBlocked(status)) {
                totalCorretamenteRejeitadas++;
            } else {
                falhas.add("token ausente -> status " + status);
            }
        }

        // 2) Requisições com token inválido (lixo, assinatura errada, expirado, esquema errado...).
        for (String header : invalidAuthHeaders()) {
            for (RequestBuilder request : protectedEndpoints(header)) {
                totalRequisicoesInvalidas++;
                int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
                if (isBlocked(status)) {
                    totalCorretamenteRejeitadas++;
                } else {
                    falhas.add("header [" + header + "] -> status " + status);
                }
            }
        }

        double X = (double) totalCorretamenteRejeitadas / totalRequisicoesInvalidas;

        System.out.printf(
                "[MÉTRICA SEGURANÇA] A=%d rejeitadas / B=%d inválidas -> X = %.4f%n",
                totalCorretamenteRejeitadas, totalRequisicoesInvalidas, X);

        assertThat(falhas)
                .as("Brecha de segurança: rotas que NÃO bloquearam acesso não autorizado")
                .isEmpty();
        assertThat(X)
                .as("Taxa de Bloqueio de Acesso Não Autorizado (X = A/B), ideal = 1")
                .isEqualTo(1.0);
    }

    // ─────────────────────────────────────────────
    // Isolamento entre usuários (token de "outro usuário")
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("isolamento: o token de um usuário só dá acesso aos próprios dados, nunca aos de outro")
    void tokenDeUmUsuario_naoAcessaDadosDeOutroUsuario() throws Exception {
        String tokenA = registrarEObterToken("user-a@test.com", "Usuário A");
        String tokenB = registrarEObterToken("user-b@test.com", "Usuário B");

        String emailRetornadoComTokenA = emailDoMe(tokenA);
        String emailRetornadoComTokenB = emailDoMe(tokenB);

        // Cada token devolve estritamente o dono — nunca vaza dados do outro usuário.
        assertThat(emailRetornadoComTokenA).isEqualTo("user-a@test.com");
        assertThat(emailRetornadoComTokenB).isEqualTo("user-b@test.com");
        assertThat(emailRetornadoComTokenA).isNotEqualTo(emailRetornadoComTokenB);
    }

    @Test
    @DisplayName("isolamento: token forjado com a identidade de outro usuário (assinatura inválida) é bloqueado")
    void tokenForjadoComIdentidadeDeOutroUsuario_eBloqueado() throws Exception {
        // Registra a vítima para garantir que o id existe no banco.
        String tokenVitima = registrarEObterToken("vitima@test.com", "Vítima");
        UUID idVitima = UUID.fromString(
                objectMapper.readTree(decodePayload(tokenVitima)).get("sub").asText());

        // Atacante forja um token com o id da vítima, mas assinado com o segredo errado.
        SecretKey wrongKey = Keys.hmacShaKeyFor(WRONG_SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        String tokenForjado = Jwts.builder()
                .subject(idVitima.toString())
                .claim("email", "vitima@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 900_000))
                .signWith(wrongKey)
                .compact();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenForjado))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    private String registrarEObterToken(String email, String nome) throws Exception {
        RegisterRequest request = new RegisterRequest(nome, email, "senha123", LocalDate.of(1990, 1, 1));
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String emailDoMe(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("email").asText();
    }

    private String decodePayload(String jwt) {
        String payload = jwt.split("\\.")[1];
        return new String(java.util.Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
    }
}
