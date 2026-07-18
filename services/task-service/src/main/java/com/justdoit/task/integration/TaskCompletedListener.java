package com.justdoit.task.integration;
import com.justdoit.task.feature.task.TaskCompletedEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia a notificação de tarefa concluída DEPOIS do commit — se a transação
 * fizer rollback, nenhuma notificação falsa é criada. O envio em si é
 * best-effort (o NotificationClient engole falhas).
 */
@Component
@RequiredArgsConstructor
public class TaskCompletedListener {

    private final NotificationClient notificationClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCompleted(TaskCompletedEvent event) {
        notificationClient.notifyTaskCompleted(event.authorizationHeader(), event.taskId(), event.title());
    }
}
