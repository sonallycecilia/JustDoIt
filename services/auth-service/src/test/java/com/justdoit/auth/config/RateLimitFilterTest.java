package com.justdoit.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final int CAPACITY = 3;

    private RateLimitFilter filtroAtivo() {
        // refill de 0,0001/min: na prática não repõe fichas durante o teste
        return new RateLimitFilter(true, CAPACITY, 0.0001);
    }

    private MockHttpServletRequest requisicao(String path, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        return request;
    }

    private int status(RateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("deve retornar 429 quando um IP excede o limite em um endpoint público")
    void deveRetornar429_quandoIpExcedeOLimite() throws Exception {
        RateLimitFilter filter = filtroAtivo();

        for (int i = 0; i < CAPACITY; i++) {
            assertThat(status(filter, requisicao("/auth/login", "10.0.0.1"))).isEqualTo(200);
        }
        assertThat(status(filter, requisicao("/auth/login", "10.0.0.1"))).isEqualTo(429);
    }

    @Test
    @DisplayName("o limite deve ser por IP: outro IP não é afetado pelo estouro do primeiro")
    void limiteDeveSerPorIp() throws Exception {
        RateLimitFilter filter = filtroAtivo();

        for (int i = 0; i <= CAPACITY; i++) {
            status(filter, requisicao("/auth/check-email", "10.0.0.1")); // estoura o IP 1
        }
        assertThat(status(filter, requisicao("/auth/check-email", "10.0.0.2"))).isEqualTo(200);
    }

    @Test
    @DisplayName("deve usar o primeiro IP do X-Forwarded-For quando presente (atrás do proxy)")
    void deveUsarXForwardedFor_quandoPresente() throws Exception {
        RateLimitFilter filter = filtroAtivo();

        for (int i = 0; i < CAPACITY; i++) {
            MockHttpServletRequest request = requisicao("/auth/login", "127.0.0.1"); // proxy
            request.addHeader("X-Forwarded-For", "203.0.113.7, 127.0.0.1");
            assertThat(status(filter, request)).isEqualTo(200);
        }
        MockHttpServletRequest excedente = requisicao("/auth/login", "127.0.0.1");
        excedente.addHeader("X-Forwarded-For", "203.0.113.7, 127.0.0.1");
        assertThat(status(filter, excedente)).isEqualTo(429);
    }

    @Test
    @DisplayName("rotas fora dos endpoints públicos de auth não devem ser limitadas")
    void naoDeveLimitarRotasProtegidas() throws Exception {
        RateLimitFilter filter = filtroAtivo();

        for (int i = 0; i < CAPACITY * 3; i++) {
            assertThat(status(filter, requisicao("/auth/me", "10.0.0.1"))).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("com enabled=false o filtro não deve limitar nada")
    void naoDeveLimitar_quandoDesabilitado() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(false, CAPACITY, 0.0001);

        for (int i = 0; i < CAPACITY * 3; i++) {
            assertThat(status(filter, requisicao("/auth/login", "10.0.0.1"))).isEqualTo(200);
        }
    }
}
