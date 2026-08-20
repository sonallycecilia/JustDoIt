-- Tarefas concluídas continuam no task-service para dashboard e relatórios.
-- Apenas o registro derivado no domínio de notificações deixa de existir.
DELETE FROM notification
WHERE type = 'TASK_COMPLETED';

UPDATE notification_preference
SET notify_on_complete = FALSE;
