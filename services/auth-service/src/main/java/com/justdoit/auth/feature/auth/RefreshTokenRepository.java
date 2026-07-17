package com.justdoit.auth.feature.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUserId(UUID userId);
    long deleteByExpiresAtBefore(LocalDateTime moment);
}
