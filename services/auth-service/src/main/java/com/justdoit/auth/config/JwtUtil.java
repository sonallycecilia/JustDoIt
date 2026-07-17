package com.justdoit.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    // iss/aud fixos: o auth-service é o único emissor e os tokens só valem para a
    // API do JustDoIt. Os serviços consumidores exigem exatamente estes valores.
    public static final String ISSUER = "justdoit-auth-service";
    public static final String AUDIENCE = "justdoit-api";

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
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
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

    public boolean validateToken(String token) {
        try {
            // Além de assinatura e expiração, exige que o token seja um ACCESS
            // token emitido por este auth-service para a API do JustDoIt — um JWT
            // de outro tipo/emissor assinado com o mesmo segredo não é aceito.
            Jwts.parser()
                    .verifyWith(getKey())
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .require("type", "access")
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaims(token).getSubject());
    }

    public String extractEmail(String token) {
        return extractClaims(token).get("email", String.class);
    }

    public String extractProfile(String token) {
        return extractClaims(token).get("profile", String.class);
    }
}
