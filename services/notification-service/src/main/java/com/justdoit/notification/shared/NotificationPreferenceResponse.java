package com.justdoit.notification.shared;

import java.util.UUID;

public record NotificationPreferenceResponse(
    UUID id,
    UUID userId,
    Boolean notifyOnComplete,
    Boolean notifyOnOverdue,
    Boolean notifyOnCycleReset
) {}
