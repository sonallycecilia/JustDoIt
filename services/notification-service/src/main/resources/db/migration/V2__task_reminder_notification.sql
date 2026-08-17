ALTER TABLE notification
    ADD COLUMN scheduled_for DATETIME(6) NULL AFTER is_read,
    ADD COLUMN expires_at DATETIME(6) NULL AFTER scheduled_for;

CREATE INDEX idx_notification_reminder
    ON notification (type, task_id, scheduled_for, expires_at);
