-- O painel de alertas exibe somente lembretes de tarefas pendentes prÃ³ximas do prazo.
DELETE FROM notification
WHERE type <> 'TASK_REMINDER';
