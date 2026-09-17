package com.justdoit.notification.feature.notification;

import com.justdoit.notification.shared.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReminderJob {

    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final NotificationRepository notificationRepository;

    @Scheduled(fixedDelayString = "${app.reminders.poll-ms:15000}")
    @Transactional
    public void synchronizeReminders() {
        int removed = removeStaleReminders();
        List<ReminderCandidate> candidates = findDueCandidates();
        int created = 0;

        for (ReminderCandidate candidate : candidates) {
            if (notificationRepository.existsByTaskIdAndType(candidate.taskId(), NotificationType.TASK_REMINDER)) {
                continue;
            }
            Notification reminder = Notification.builder()
                    .userId(candidate.userId())
                    .taskId(candidate.taskId())
                    .type(NotificationType.TASK_REMINDER)
                    .title("Tarefa se aproximando")
                    .message("A tarefa \"" + candidate.title() + "\" vence em "
                            + candidate.dueAt().format(DUE_FORMAT) + ".")
                    .read(false)
                    .scheduledFor(candidate.scheduledFor())
                    .expiresAt(candidate.dueAt())
                    .build();
            notificationRepository.save(reminder);
            created++;
        }

        if (removed > 0 || created > 0) {
            log.info("Lembretes sincronizados: {} criado(s), {} removido(s)", created, removed);
        }
    }

    private int removeStaleReminders() {
        return jdbcTemplate.update("""
                DELETE n FROM notification n
                LEFT JOIN task t ON t.id = n.task_id
                WHERE n.type = 'TASK_REMINDER'
                  AND (
                    t.id IS NULL
                    OR COALESCE(t.status, 'PENDING') = 'COMPLETED'
                    OR t.reminder_minutes_before IS NULL
                    OR t.due_date IS NULL
                    OR t.due_time IS NULL
                    OR TIMESTAMP(t.due_date, t.due_time) <= CURRENT_TIMESTAMP()
                    OR NOT (n.expires_at <=> TIMESTAMP(t.due_date, t.due_time))
                    OR NOT (n.scheduled_for <=> DATE_SUB(
                        TIMESTAMP(t.due_date, t.due_time),
                        INTERVAL t.reminder_minutes_before MINUTE
                    ))
                  )
                """);
    }

    private List<ReminderCandidate> findDueCandidates() {
        return jdbcTemplate.query("""
                SELECT t.id, t.user_id, t.title,
                       TIMESTAMP(t.due_date, t.due_time) AS due_at,
                       DATE_SUB(
                           TIMESTAMP(t.due_date, t.due_time),
                           INTERVAL t.reminder_minutes_before MINUTE
                       ) AS scheduled_for
                FROM task t
                WHERE COALESCE(t.status, 'PENDING') <> 'COMPLETED'
                  AND t.reminder_minutes_before IS NOT NULL
                  AND t.due_date IS NOT NULL
                  AND t.due_time IS NOT NULL
                  AND TIMESTAMP(t.due_date, t.due_time) > CURRENT_TIMESTAMP()
                  AND DATE_SUB(
                        TIMESTAMP(t.due_date, t.due_time),
                        INTERVAL t.reminder_minutes_before MINUTE
                      ) <= CURRENT_TIMESTAMP()
                """, (rs, rowNum) -> new ReminderCandidate(
                uuid(rs.getBytes("id")),
                uuid(rs.getBytes("user_id")),
                rs.getString("title"),
                timestamp(rs.getTimestamp("due_at")),
                timestamp(rs.getTimestamp("scheduled_for"))
        ));
    }

    private static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static LocalDateTime timestamp(Timestamp value) {
        return value.toLocalDateTime();
    }

    record ReminderCandidate(UUID taskId, UUID userId, String title,
                             LocalDateTime dueAt, LocalDateTime scheduledFor) {}
}
