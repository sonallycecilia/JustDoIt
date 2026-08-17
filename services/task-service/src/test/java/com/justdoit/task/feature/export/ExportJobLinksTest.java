package com.justdoit.task.feature.export;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobLinksTest {

    @Test
    void freshlyGeneratedLinkIsValidIndependentlyOfLocalTimezone() {
        ExportProperties properties = new ExportProperties();
        properties.setPublicApiBaseUrl("https://api.example.test/");
        TemporaryDownloadTokenService tokens = new TemporaryDownloadTokenService(
                "temporary-download-test-secret-at-least-32-bytes");
        ExportJobLinks links = new ExportJobLinks(properties, tokens);
        ExportJob job = ExportJob.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .downloadExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(15))
                .build();

        String url = links.downloadUrl(job);
        var query = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        long expires = Long.parseLong(query.getFirst("expires"));

        assertThat(url).startsWith("https://api.example.test/me/exports/");
        assertThat(tokens.validate(job.getId(), job.getUserId(), expires,
                query.getFirst("token"))).isTrue();
    }
}
