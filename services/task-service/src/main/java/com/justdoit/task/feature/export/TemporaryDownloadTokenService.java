package com.justdoit.task.feature.export;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class TemporaryDownloadTokenService {

    private static final String HMAC = "HmacSHA256";
    private final byte[] secret;

    public TemporaryDownloadTokenService(
            @Value("${app.export.download-secret:${jwt.secret}}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String create(UUID jobId, UUID userId, long expiresEpochSeconds) {
        return sign(payload(jobId, userId, expiresEpochSeconds));
    }

    public boolean validate(UUID jobId, UUID userId, long expiresEpochSeconds, String token) {
        if (token == null || expiresEpochSeconds <= Instant.now().getEpochSecond()) return false;
        byte[] expected = create(jobId, userId, expiresEpochSeconds).getBytes(StandardCharsets.US_ASCII);
        byte[] provided = token.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, provided);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o link de download", e);
        }
    }

    private static String payload(UUID jobId, UUID userId, long expiresEpochSeconds) {
        return jobId + ":" + userId + ":" + expiresEpochSeconds;
    }
}
