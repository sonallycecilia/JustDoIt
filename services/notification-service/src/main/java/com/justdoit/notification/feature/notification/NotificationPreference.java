package com.justdoit.notification.feature.notification;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_preference")
public class NotificationPreference {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Builder.Default
    @Column(name = "notify_on_complete")
    private Boolean notifyOnComplete = true;

    @Builder.Default
    @Column(name = "notify_on_overdue")
    private Boolean notifyOnOverdue = true;

    @Builder.Default
    @Column(name = "notify_on_cycle_reset")
    private Boolean notifyOnCycleReset = true;
}
