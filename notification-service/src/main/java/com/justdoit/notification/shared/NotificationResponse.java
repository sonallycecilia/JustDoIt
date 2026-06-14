package com.justdoit.notification.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID userId,
    UUID taskId,
    NotificationType type,
    String title,
    String message,
    Boolean read,
    LocalDateTime createdAt
) {}
