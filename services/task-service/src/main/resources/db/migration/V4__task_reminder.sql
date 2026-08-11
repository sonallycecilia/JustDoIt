ALTER TABLE task
    ADD COLUMN reminder_minutes_before INT NULL AFTER due_time;

CREATE INDEX idx_task_reminder_due
    ON task (status, due_date, due_time, reminder_minutes_before);
