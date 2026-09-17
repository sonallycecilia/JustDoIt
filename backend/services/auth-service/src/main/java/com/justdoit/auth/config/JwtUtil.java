package com.justdoit.auth.config;

import com.justdoit.common.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Geração de access tokens. O auth-service é o ÚNICO emissor de tokens do
 * JustDoIt; a validação e a leitura ficam no {@link JwtValidator} do libs/common,
 * reusado por todos os serviços. iss/aud vêm de lá para haver uma única fonte de
 * verdade.
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration-ms:900000}") // 15 min
    private long accessTokenExpirationMs;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, String email, String profile) {
        return Jwts.builder()
                // jti: identificador único do token — permite revogação individual
                // (blacklist) no futuro sem mudar o contrato.
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .issuer(JwtValidator.ISSUER)
                .audience().add(JwtValidator.AUDIENCE).and()
                .claim("email", email)
                .claim("profile", profile)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(getKey())
                .compact();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
