package com.justdoit.notification.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Devem espelhar os valores emitidos pelo auth-service (JwtUtil de lá).
    private static final String ISSUER = "justdoit-auth-service";
    private static final String AUDIENCE = "justdoit-api";

    public boolean validateToken(String token) {
        try {
            // Além de assinatura e expiração, exige que o token seja um ACCESS
            // token emitido pelo auth-service para a API do JustDoIt — um JWT de
            // outro tipo/emissor assinado com o mesmo segredo não é aceito.
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
}
