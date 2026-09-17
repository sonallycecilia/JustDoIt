package com.justdoit.task.feature.export;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class ExportJobLinks {

    private final ExportProperties properties;
    private final TemporaryDownloadTokenService tokenService;

    public String statusPath(ExportJob job) {
        return "/me/exports/" + job.getId();
    }

    public String downloadUrl(ExportJob job) {
        long expires = job.getDownloadExpiresAt().toEpochSecond(ZoneOffset.UTC);
        String token = tokenService.create(job.getId(), job.getUserId(), expires);
        return UriComponentsBuilder.fromUriString(trimSlash(properties.getPublicApiBaseUrl()))
                .path("/me/exports/{id}/download")
                .queryParam("expires", expires)
                .queryParam("token", token)
                .buildAndExpand(job.getId())
                .toUriString();
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
