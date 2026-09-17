-- Bancos criados antes do baseline Flyway usavam ENUM e não aceitavam o tipo
-- TASK_REMINDER. VARCHAR mantém compatibilidade com @Enumerated(EnumType.STRING)
-- e permite adicionar novos tipos pela aplicação sem alterar o schema.
ALTER TABLE notification
    MODIFY COLUMN type VARCHAR(255) NOT NULL;
