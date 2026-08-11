package com.justdoit.task.feature.export;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemporaryDownloadTokenServiceTest {

    private final TemporaryDownloadTokenService service =
            new TemporaryDownloadTokenService("temporary-download-test-secret-at-least-32-bytes");

    @Test
    @DisplayName("link válido é vinculado ao job, usuário e expiração")
    void validTokenIsBoundToClaims() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long expires = Instant.now().plusSeconds(60).getEpochSecond();
        String token = service.create(jobId, userId, expires);

        assertThat(service.validate(jobId, userId, expires, token)).isTrue();
        assertThat(service.validate(UUID.randomUUID(), userId, expires, token)).isFalse();
        assertThat(service.validate(jobId, UUID.randomUUID(), expires, token)).isFalse();
        assertThat(service.validate(jobId, userId, expires + 1, token)).isFalse();
    }

    @Test
    @DisplayName("link expirado é rejeitado mesmo com assinatura correta")
    void expiredTokenIsRejected() {
        UUID jobId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        long expires = Instant.now().minusSeconds(1).getEpochSecond();

        assertThat(service.validate(jobId, userId, expires,
                service.create(jobId, userId, expires))).isFalse();
    }
}
