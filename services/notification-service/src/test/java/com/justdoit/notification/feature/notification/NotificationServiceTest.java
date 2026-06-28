package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @InjectMocks private NotificationService service;

    private static final UUID USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTIF_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PREF_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Notification notification;
    private NotificationPreference preference;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .id(NOTIF_ID).userId(USER_ID)
                .type(NotificationType.TASK_COMPLETED)
                .title("Task done").message("Your task was completed")
                .read(false).createdAt(LocalDateTime.now())
                .build();

        preference = NotificationPreference.builder()
                .id(PREF_ID).userId(USER_ID)
                .notifyOnComplete(true).notifyOnOverdue(true).notifyOnCycleReset(true)
                .build();
    }

    @Test
    void createNotification_savesAndReturnsResponse() {
        CreateNotificationRequest request = new CreateNotificationRequest(
                null, NotificationType.TASK_COMPLETED, "Task done", "Your task was completed");
        when(notificationRepository.save(any())).thenReturn(notification);

        NotificationResponse result = service.createNotification(request, USER_ID);

        assertEquals(NOTIF_ID, result.id());
        assertEquals(USER_ID, result.userId());
        assertEquals(NotificationType.TASK_COMPLETED, result.type());
        assertFalse(result.read());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void markAsRead_setsReadTrue() {
        Notification readNotif = Notification.builder()
                .id(NOTIF_ID).userId(USER_ID)
                .type(NotificationType.TASK_COMPLETED)
                .title("Task done").message("Your task was completed")
                .read(true).createdAt(LocalDateTime.now())
                .build();
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any())).thenReturn(readNotif);

        NotificationResponse result = service.markAsRead(NOTIF_ID, USER_ID);

        assertTrue(result.read());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_notFound_throwsException() {
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(NOTIF_ID, USER_ID));
    }

    @Test
    void markAsRead_wrongUser_throwsException() {
        UUID otherUser = UUID.randomUUID();
        when(notificationRepository.findById(NOTIF_ID)).thenReturn(Optional.of(notification));

        assertThrows(IllegalArgumentException.class, () -> service.markAsRead(NOTIF_ID, otherUser));
    }

    @Test
    void getUnreadByUser_returnsList() {
        when(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = service.getUnreadByUser(USER_ID);

        assertEquals(1, result.size());
        assertEquals(NOTIF_ID, result.get(0).id());
        assertFalse(result.get(0).read());
    }

    @Test
    void getAllByUser_returnsList() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(notification));

        List<NotificationResponse> result = service.getAllByUser(USER_ID);

        assertEquals(1, result.size());
        assertEquals(NOTIF_ID, result.get(0).id());
    }

    @Test
    void getOrCreatePreference_whenExists_returnsExisting() {
        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.of(preference));

        NotificationPreferenceResponse result = service.getOrCreatePreference(USER_ID);

        assertEquals(PREF_ID, result.id());
        assertTrue(result.notifyOnComplete());
        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void getOrCreatePreference_whenAbsent_createsNew() {
        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(preferenceRepository.save(any())).thenReturn(preference);

        NotificationPreferenceResponse result = service.getOrCreatePreference(USER_ID);

        assertEquals(PREF_ID, result.id());
        verify(preferenceRepository).save(any(NotificationPreference.class));
    }

    @Test
    void updatePreference_updatesOnlyProvidedFields() {
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(false, null, null);
        NotificationPreference updated = NotificationPreference.builder()
                .id(PREF_ID).userId(USER_ID)
                .notifyOnComplete(false).notifyOnOverdue(true).notifyOnCycleReset(true)
                .build();
        when(preferenceRepository.findByUserId(USER_ID)).thenReturn(Optional.of(preference));
        when(preferenceRepository.save(any())).thenReturn(updated);

        NotificationPreferenceResponse result = service.updatePreference(USER_ID, request);

        assertFalse(result.notifyOnComplete());
        assertTrue(result.notifyOnOverdue());
    }
}
