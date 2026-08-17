-- task.cycle_id foi criado na V2 apenas com índice, sem chave estrangeira.
-- Bases anteriores podem conter tarefas válidas apontando para ciclos ausentes;
-- nessas tarefas qualquer mutação passava pelo CycleMutabilityGuard e virava 404.

UPDATE task t
LEFT JOIN weekly_cycles wc ON wc.id = t.cycle_id
SET t.cycle_id = NULL
WHERE t.cycle_id IS NOT NULL
  AND wc.id IS NULL;

-- Ciclos não são removidos no fluxo normal. ON DELETE SET NULL mantém a tarefa
-- utilizável mesmo em uma eventual limpeza administrativa do histórico.
ALTER TABLE task
    ADD CONSTRAINT fk_task_weekly_cycle
    FOREIGN KEY (cycle_id) REFERENCES weekly_cycles (id)
    ON DELETE SET NULL;
