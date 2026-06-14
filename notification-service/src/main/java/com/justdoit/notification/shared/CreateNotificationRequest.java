package com.justdoit.notification.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateNotificationRequest(
    @NotNull UUID userId,
    UUID taskId,
    @NotNull NotificationType type,
    @NotBlank String title,
    @NotBlank String message
) {}
