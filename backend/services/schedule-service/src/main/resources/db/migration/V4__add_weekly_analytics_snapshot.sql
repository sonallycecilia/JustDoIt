-- Congela o contrato completo usado pelos gráficos. Resumos anteriores ficam
-- NULL e são explicitamente expostos como dados reconstruídos/parciais.
ALTER TABLE weekly_summary
    ADD COLUMN analytics_payload LONGTEXT NULL;
