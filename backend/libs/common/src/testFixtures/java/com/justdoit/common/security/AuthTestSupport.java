package com.justdoit.common.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Apoio a testes de slice (@WebMvcTest) dos serviços que consomem o
 * {@link JwtAuthFilter}. Em runtime o filtro coloca o UUID do usuário como
 * principal; este helper reproduz o mesmo principal para que controllers que
 * recebem {@code @AuthenticationPrincipal UUID userId} funcionem nos testes.
 */
public final class AuthTestSupport {

    private AuthTestSupport() {
    }

    public static RequestPostProcessor authenticatedUser(UUID userId) {
        return authentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
