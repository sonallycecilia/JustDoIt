package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional
    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = Notification.builder()
                .userId(request.userId())
                .taskId(request.taskId())
                .type(request.type())
                .title(request.title())
                .message(request.message())
                .read(false)
                .build();
        return toResponse(notificationRepository.save(notification));
    }

    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    public List<NotificationResponse> getUnreadByUser(UUID userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<NotificationResponse> getAllByUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public NotificationPreferenceResponse getOrCreatePreference(UUID userId) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference newPref = NotificationPreference.builder()
                            .userId(userId)
                            .notifyOnComplete(true)
                            .notifyOnOverdue(true)
                            .notifyOnCycleReset(true)
                            .build();
                    return preferenceRepository.save(newPref);
                });
        return toPrefResponse(pref);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId, NotificationPreferenceRequest request) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElse(NotificationPreference.builder().userId(userId).build());
        if (request.notifyOnComplete() != null) pref.setNotifyOnComplete(request.notifyOnComplete());
        if (request.notifyOnOverdue() != null) pref.setNotifyOnOverdue(request.notifyOnOverdue());
        if (request.notifyOnCycleReset() != null) pref.setNotifyOnCycleReset(request.notifyOnCycleReset());
        return toPrefResponse(preferenceRepository.save(pref));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getUserId(), n.getTaskId(),
                n.getType(), n.getTitle(), n.getMessage(), n.getRead(), n.getCreatedAt());
    }

    private NotificationPreferenceResponse toPrefResponse(NotificationPreference p) {
        return new NotificationPreferenceResponse(p.getId(), p.getUserId(),
                p.getNotifyOnComplete(), p.getNotifyOnOverdue(), p.getNotifyOnCycleReset());
    }
}
