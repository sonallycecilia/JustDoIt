package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReminderJobTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private NotificationRepository notificationRepository;

    private TaskReminderJob job;

    @BeforeEach
    void setUp() {
        job = new TaskReminderJob(jdbcTemplate, notificationRepository);
        when(jdbcTemplate.update(anyString())).thenReturn(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createsReminderForCandidateInsideAlertWindow() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime dueAt = LocalDateTime.of(2026, 8, 11, 10, 0);
        TaskReminderJob.ReminderCandidate candidate = new TaskReminderJob.ReminderCandidate(
                taskId, userId, "Reunião", dueAt, dueAt.minusMinutes(15));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(candidate));
        when(notificationRepository.existsByTaskIdAndType(taskId, NotificationType.TASK_REMINDER))
                .thenReturn(false);

        job.synchronizeReminders();

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(taskId, saved.getTaskId());
        assertEquals(userId, saved.getUserId());
        assertEquals(NotificationType.TASK_REMINDER, saved.getType());
        assertEquals(dueAt, saved.getExpiresAt());
        assertEquals(dueAt.minusMinutes(15), saved.getScheduledFor());
        assertFalse(saved.getRead());
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotDuplicateExistingReminder() {
        UUID taskId = UUID.randomUUID();
        LocalDateTime dueAt = LocalDateTime.now().plusHours(1);
        TaskReminderJob.ReminderCandidate candidate = new TaskReminderJob.ReminderCandidate(
                taskId, UUID.randomUUID(), "Tarefa", dueAt, dueAt.minusMinutes(15));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(candidate));
        when(notificationRepository.existsByTaskIdAndType(taskId, NotificationType.TASK_REMINDER))
                .thenReturn(true);

        job.synchronizeReminders();

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}
