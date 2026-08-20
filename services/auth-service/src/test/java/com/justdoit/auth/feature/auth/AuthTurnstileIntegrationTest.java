package com.justdoit.auth.feature.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.auth.shared.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Contrato HTTP do Turnstile na autenticação")
class AuthTurnstileIntegrationTest {

    private static final String TURNSTILE_HEADER = "X-Turnstile-Token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TurnstileService turnstileService;

    @Test
    @DisplayName("registro sem token é bloqueado com 403")
    void blocksRegistrationWithoutToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("missing-token@test.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Falha na verificação de segurança. Acesso bloqueado."));

        verify(turnstileService).isValid(null);
    }

    @Test
    @DisplayName("registro com token inválido é bloqueado com 403")
    void blocksRegistrationWithInvalidToken() throws Exception {
        when(turnstileService.isValid("invalid-token")).thenReturn(false);

        mockMvc.perform(post("/auth/register")
                        .header(TURNSTILE_HEADER, "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("invalid-token@test.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Falha na verificação de segurança. Acesso bloqueado."));
    }

    @Test
    @DisplayName("registro com token válido continua disponível")
    void acceptsRegistrationWithValidToken() throws Exception {
        when(turnstileService.isValid("valid-token")).thenReturn(true);

        mockMvc.perform(post("/auth/register")
                        .header(TURNSTILE_HEADER, "valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("valid-token@test.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @DisplayName("indisponibilidade da Cloudflare resulta em 502")
    void reportsTurnstileUnavailability() throws Exception {
        when(turnstileService.isValid("unavailable-token"))
                .thenThrow(new RuntimeException("Serviço de verificação indisponível no momento. Tente novamente."));

        mockMvc.perform(post("/auth/register")
                        .header(TURNSTILE_HEADER, "unavailable-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRegistration("unavailable-token@test.com")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error")
                        .value("Serviço de verificação indisponível no momento. Tente novamente."));
    }

    private String validRegistration(String email) throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Turnstile Test User",
                email,
                "Senha123456",
                LocalDate.of(1990, 1, 1));
        return objectMapper.writeValueAsString(request);
    }
}
