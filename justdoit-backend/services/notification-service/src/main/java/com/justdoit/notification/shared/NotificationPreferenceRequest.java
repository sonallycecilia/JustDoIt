package com.justdoit.notification.shared;

public record NotificationPreferenceRequest(
    Boolean notifyOnComplete,
    Boolean notifyOnOverdue,
    Boolean notifyOnCycleReset
) {}
