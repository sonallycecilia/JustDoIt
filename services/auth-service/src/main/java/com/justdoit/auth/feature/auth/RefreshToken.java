package com.justdoit.auth.feature.auth;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // Guardamos apenas o hash SHA-256 do refresh token, nunca o valor em claro.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    private String profile;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Preenchido quando o token é rotacionado (usado num /auth/refresh). A linha
    // vira uma "lápide": se o mesmo token for apresentado de novo, é reuso —
    // todas as sessões do usuário são revogadas. Removida pela limpeza periódica.
    @Column(name = "used_at")
    private LocalDateTime usedAt;
}
