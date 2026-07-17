package com.justdoit.task.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de contrato: garante que o access token exatamente como o auth-service
 * emite (iss/aud/type/jti) é aceito aqui, e que JWTs assinados com o MESMO
 * segredo mas sem esses claims são rejeitados — é isso que impede outro serviço
 * (ou outro tipo de token) de se passar por access token.
 */
class JwtUtilTest {

    private static final String SECRET =
            "test-secret-key-please-change-256-bits-minimum-0123456789abcdef";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
    }

    /** Espelha fielmente o JwtUtil.generateAccessToken do auth-service. */
    private String tokenComoOAuthServiceEmite(UnaryOperator<io.jsonwebtoken.JwtBuilder> customizer) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(UUID.randomUUID().toString())
                .issuer("justdoit-auth-service")
                .audience().add("justdoit-api").and()
                .claim("email", "user@test.com")
                .claim("profile", "USER")
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000));
        return customizer.apply(builder).signWith(key).compact();
    }

    @Test
    @DisplayName("aceita o access token no formato emitido pelo auth-service")
    void deveAceitarTokenDoAuthService() {
        assertThat(jwtUtil.validateToken(tokenComoOAuthServiceEmite(b -> b))).isTrue();
    }

    @Test
    @DisplayName("rejeita JWT com o mesmo segredo mas sem type=access")
    void deveRejeitarToken_semTypeAccess() {
        String token = tokenComoOAuthServiceEmite(b -> b.claim("type", "outro"));
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("rejeita JWT com o mesmo segredo mas com issuer diferente")
    void deveRejeitarToken_comIssuerErrado() {
        String token = tokenComoOAuthServiceEmite(b -> b.issuer("outro-emissor"));
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("rejeita JWT com o mesmo segredo mas com audience diferente")
    void deveRejeitarToken_comAudienceErrada() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer("justdoit-auth-service")
                .audience().add("outra-api").and()
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("rejeita JWT 'legado' (sem iss/aud/type), mesmo bem assinado")
    void deveRejeitarTokenLegado_semClaimsDeContexto() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000))
                .signWith(key)
                .compact();
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }
}
