package com.justdoit.notification.feature.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupportMessageRequest(
        @NotBlank(message = "A mensagem é obrigatória")
        @Size(max = 4000, message = "A mensagem deve ter no máximo 4000 caracteres")
        String content,

        @Size(max = 2048, message = "A URL da página deve ter no máximo 2048 caracteres")
        String pageUrl,

        @Size(max = 512, message = "A identificação do navegador deve ter no máximo 512 caracteres")
        String userAgent
) {
}
