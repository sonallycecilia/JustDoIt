package com.justdoit.auth.feature.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RefreshRequest;
import com.justdoit.auth.shared.RegisterRequest;
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

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL = "integration@test.com";
    private static final String PASSWORD = "senha123";
    private static final RegisterRequest VALID_REGISTER = new RegisterRequest(
            "Integration User", EMAIL, PASSWORD, LocalDate.of(1990, 6, 15)
    );

    // ─────────────────────────────────────────────
    // POST /auth/register
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("register: deve retornar 201 e token quando dados são válidos")
    void register_deveRetornar201EToken_quandoDadosValidos() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("register: deve retornar 400 com mensagem quando email já existe")
    void register_deveRetornar400_quandoEmailDuplicado() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email já cadastrado"));
    }

    @Test
    @DisplayName("register: deve retornar 400 com erros de campo quando dados são inválidos")
    void register_deveRetornar400ComErrosDeCampo_quandoDadosInvalidos() throws Exception {
        RegisterRequest invalid = new RegisterRequest("", "emailinvalido", "123", null);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.email").isNotEmpty())
                .andExpect(jsonPath("$.password").isNotEmpty())
                .andExpect(jsonPath("$.birthDate").isNotEmpty());
    }

    // ─────────────────────────────────────────────
    // POST /auth/login
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("login: deve retornar 200 e token quando credenciais são válidas")
    void login_deveRetornar200EToken_quandoCredenciaisValidas() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("login: deve retornar 401 quando email não existe")
    void login_deveRetornar401_quandoEmailNaoExiste() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("naoexiste@test.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
    }

    @Test
    @DisplayName("login: deve retornar 401 quando senha está errada")
    void login_deveRetornar401_quandoSenhaErrada() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, "senha_errada"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
    }

    @Test
    @DisplayName("login: não deve revelar se o erro foi no email ou na senha")
    void login_deveDarMesmaRespostaPraEmailESenhaErrados() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated());

        MvcResult emailErrado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("outro@test.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult senhaErrada = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(EMAIL, "senha_errada"))))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String msgEmailErrado = objectMapper.readTree(emailErrado.getResponse().getContentAsString()).get("error").asText();
        String msgSenhaErrada = objectMapper.readTree(senhaErrada.getResponse().getContentAsString()).get("error").asText();

        org.assertj.core.api.Assertions.assertThat(msgEmailErrado).isEqualTo(msgSenhaErrada);
    }

    // ─────────────────────────────────────────────
    // GET /auth/me
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("me: deve retornar 200 com dados do usuário quando autenticado")
    void me_deveRetornar200ComDadosDoUsuario_quandoAutenticado() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Integration User"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.birthDate").value("1990-06-15"));
    }

    @Test
    @DisplayName("me: deve retornar 403 quando não autenticado")
    void me_deveRetornar403_quandoNaoAutenticado() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // POST /auth/logout
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("logout: deve retornar 204 quando autenticado")
    void logout_deveRetornar204_quandoAutenticado() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andReturn();

        String token = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("logout: deve retornar 403 quando não autenticado")
    void logout_deveRetornar403_quandoNaoAutenticado() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────
    // POST /auth/refresh
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("refresh: deve retornar 200 com novo access token quando o refresh token é válido")
    void refresh_deveRetornar200ComNovoToken_quandoRefreshTokenValido() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andReturn();

        String refreshToken = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("refresh: deve retornar 401 quando o refresh token é inválido")
    void refresh_deveRetornar401_quandoRefreshTokenInvalido() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("token-que-nao-existe"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Refresh token inválido"));
    }

    @Test
    @DisplayName("refresh: reuso de refresh token já rotacionado revoga todas as sessões")
    void refresh_deveRevogarTodasAsSessoes_quandoTokenRotacionadoForReusado() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andReturn();

        String refreshToken1 = objectMapper.readTree(registerResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // 1º uso: rotação normal — emite o refresh token 2.
        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken1))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken2 = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        // Reuso do token 1 (cenário de roubo): 401 e revogação de TODAS as sessões.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken1))))
                .andExpect(status().isUnauthorized());

        // O token 2 (ainda não usado) também deve ter sido revogado.
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken2))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refresh: deve retornar 401 após logout (refresh token revogado)")
    void refresh_deveRetornar401_aposLogout() throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(VALID_REGISTER)))
                .andExpect(status().isCreated())
                .andReturn();

        var body = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String accessToken = body.get("accessToken").asText();
        String refreshToken = body.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
                .andExpect(status().isUnauthorized());
    }
}
