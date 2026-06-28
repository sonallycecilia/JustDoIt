package com.justdoit.backend.services;

import com.justdoit.backend.models.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

@Service
public class TokenService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final Clock clock;

        @Autowired
        public TokenService(
            @Value("${security.jwt.secret}") String jwtSecret,
            @Value("${security.jwt.expiration-ms:86400000}") long jwtExpirationMs
    ) {
        this(jwtSecret, jwtExpirationMs, Clock.systemUTC());
    }

    TokenService(String jwtSecret, long jwtExpirationMs, Clock clock) {
        validateSecret(jwtSecret);
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.clock = clock;
    }

    public String generateToken(User user) {
        return generateToken(user.getEmail());
    }

    public String generateToken(String subject) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
            .subject(subject)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getSubjectFromToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isTokenValid(String token) {
        return getSubjectFromToken(token) != null;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret nao pode ser vazio.");
        }

        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret deve ter no minimo 32 bytes para HS256.");
        }
    }
}
