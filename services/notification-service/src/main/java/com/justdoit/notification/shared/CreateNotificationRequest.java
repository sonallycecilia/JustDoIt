package com.justdoit.notification.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateNotificationRequest(
    UUID taskId,
    @NotNull NotificationType type,
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(max = 2000) String message
) {}
