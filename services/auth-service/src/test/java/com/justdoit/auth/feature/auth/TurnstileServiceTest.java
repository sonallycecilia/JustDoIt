package com.justdoit.auth.feature.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("Cloudflare Turnstile")
class TurnstileServiceTest {

    private static final String VERIFY_URL = "https://turnstile.test/siteverify";

    private MockRestServiceServer server;
    private TurnstileService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new TurnstileService(builder, VERIFY_URL, "test-secret");
    }

    @Test
    @DisplayName("rejeita token ausente sem chamar a Cloudflare")
    void rejectsMissingTokenWithoutCallingCloudflare() {
        assertThat(service.isValid(null)).isFalse();
        assertThat(service.isValid("   ")).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("aceita token validado pela Cloudflare")
    void acceptsValidToken() {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(service.isValid("valid-token")).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("rejeita token recusado pela Cloudflare")
    void rejectsInvalidToken() {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}",
                        MediaType.APPLICATION_JSON));

        assertThat(service.isValid("invalid-token")).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("falha de forma controlada quando a Cloudflare está indisponível")
    void reportsCloudflareUnavailability() {
        server.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> service.isValid("unavailable-token"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Serviço de verificação indisponível no momento. Tente novamente.");
        server.verify();
    }
}
