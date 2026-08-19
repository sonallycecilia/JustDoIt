package com.justdoit.notification.feature.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

import com.justdoit.notification.shared.NotificationType;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId);
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type);
    List<Notification> findByUserIdAndReadFalseAndTypeOrderByCreatedAtDesc(UUID userId, NotificationType type);
    long deleteByUserId(UUID userId);
    boolean existsByTaskIdAndType(UUID taskId, NotificationType type);
}
