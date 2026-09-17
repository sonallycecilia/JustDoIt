package com.justdoit.task.feature.task;
import com.justdoit.task.integration.NotificationClient;

import com.justdoit.task.shared.TaskStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Detecta tarefas atrasadas: dueDate vencida e ainda PENDING/IN_PROGRESS.
 * Marca como OVERDUE e notifica o usuário via endpoint interno do
 * notification-service (fluxo sem usuário presente — não há token para repassar).
 *
 * Regras:
 * - "vencida" = dueDate < hoje (a tarefa tem o dia inteiro do prazo como graça;
 *   dueTime é ignorado de propósito, o corte é por dia).
 * - A mudança de status garante que cada tarefa só gera UMA notificação de
 *   atraso: na próxima rodada ela já não está mais em PENDING/IN_PROGRESS.
 * - Notificação é best-effort (o client engole falhas); a marcação de OVERDUE
 *   acontece independentemente do notification-service estar de pé.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueTaskJob {

    private final TaskRepository taskRepository;
    private final NotificationClient notificationClient;

    @Scheduled(cron = "0 15 * * * *") // de hora em hora, aos 15 min
    @Transactional
    public void markOverdueTasks() {
        List<Task> overdue = taskRepository.findByStatusInAndDueDateBefore(
                List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS), LocalDate.now());
        if (overdue.isEmpty()) {
            return;
        }

        overdue.forEach(task -> task.setStatus(TaskStatus.OVERDUE));
        taskRepository.saveAll(overdue);
        log.info("{} tarefa(s) marcada(s) como OVERDUE", overdue.size());

        overdue.forEach(task ->
                notificationClient.notifyTaskOverdue(task.getUserId(), task.getId(), task.getTitle()));
    }
}
