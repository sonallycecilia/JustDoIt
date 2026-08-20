package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByUserIdAndTypeNotOrderByCreatedAtDesc(UUID userId, NotificationType excludedType);
    List<Notification> findByUserIdAndReadFalseAndTypeNotOrderByCreatedAtDesc(
            UUID userId, NotificationType excludedType);
    boolean existsByTaskIdAndType(UUID taskId, NotificationType type);
    void deleteByUserId(UUID userId);
}
