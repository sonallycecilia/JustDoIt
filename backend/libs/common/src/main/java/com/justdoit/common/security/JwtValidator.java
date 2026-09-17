package com.justdoit.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Validação e leitura de access tokens emitidos pelo auth-service.
 * Compartilhado por todos os serviços consumidores (task, schedule,
 * notification). A GERAÇÃO de tokens NÃO mora aqui — é responsabilidade
 * exclusiva do auth-service, o único emissor.
 */
@Component
public class JwtValidator {

    // iss/aud fixos: o auth-service é o único emissor e os tokens só valem para a
    // API do JustDoIt. Todos os serviços consumidores exigem exatamente estes valores.
    public static final String ISSUER = "justdoit-auth-service";
    public static final String AUDIENCE = "justdoit-api";

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

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
