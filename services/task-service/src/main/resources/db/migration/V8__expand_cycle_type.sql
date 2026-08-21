-- Bancos criados antes do baseline Flyway ainda possuem o enum legado sem
-- BIWEEKLY e CUSTOM. O código persiste CycleType com EnumType.STRING; usar texto
-- mantém a coluna alinhada ao V1 e evita uma nova migração a cada tipo futuro.
ALTER TABLE cycle_config
    MODIFY COLUMN cycle_type VARCHAR(32) NOT NULL;
