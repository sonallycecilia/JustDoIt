package com.justdoit.auth.feature.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Higiene da tabela {@code refresh_token}: tokens expirados (e as "lápides" de
 * tokens já rotacionados) só eram removidos quando o próprio usuário usava,
 * logava ou deslogava — sem isso a tabela cresceria indefinidamente.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 3 * * *") // diariamente às 03:00
    @Transactional
    public void purgeExpiredTokens() {
        long removed = refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("Limpeza de refresh tokens: {} registro(s) expirado(s) removido(s)", removed);
        }
    }
}
