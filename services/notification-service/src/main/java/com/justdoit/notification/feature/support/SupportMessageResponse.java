package com.justdoit.notification.feature.support;

import java.time.LocalDateTime;
import java.util.UUID;

public record SupportMessageResponse(
        UUID id,
        SupportMessageSender sender,
        String content,
        String pageUrl,
        LocalDateTime createdAt
) {
    public static SupportMessageResponse from(SupportMessage message) {
        return new SupportMessageResponse(
                message.getId(),
                message.getSender(),
                message.getContent(),
                message.getPageUrl(),
                message.getCreatedAt());
    }
}
