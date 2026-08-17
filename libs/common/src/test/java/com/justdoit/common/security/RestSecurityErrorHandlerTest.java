package com.justdoit.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class RestSecurityErrorHandlerTest {

    private final RestSecurityErrorHandler handler = new RestSecurityErrorHandler();

    @Test
    void credencialAusenteOuInvalidaRetorna401EmJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response,
                new BadCredentialsException("token inválido"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("Autenticação necessária");
    }

    @Test
    void usuarioAutenticadoSemPermissaoRetorna403EmJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response,
                new AccessDeniedException("sem permissão"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("Acesso negado");
    }
}
