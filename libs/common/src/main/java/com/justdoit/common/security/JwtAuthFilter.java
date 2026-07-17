package com.justdoit.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Autentica cada requisição a partir do access token no header Authorization.
 * Ao validar, coloca o UUID do usuário como principal no SecurityContext — por
 * isso os controllers podem receber {@code @AuthenticationPrincipal UUID userId}
 * sem reprocessar o token.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authHeader.substring(7);
        if (jwtValidator.validateToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    jwtValidator.extractUserId(token), null, List.of()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Por padrão o OncePerRequestFilter não roda no dispatch de erro (/error).
     * Sem isto, qualquer erro real (400 de body inválido, 405 de método, etc.) é
     * re-despachado SEM autenticação e, como toda rota exige autenticação, volta
     * para o cliente mascarado como 403. Rodar o filtro também no /error mantém a
     * autenticação e deixa o status verdadeiro chegar ao frontend.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
