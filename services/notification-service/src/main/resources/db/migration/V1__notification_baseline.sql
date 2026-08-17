-- Baseline do domínio de notificações.

CREATE TABLE IF NOT EXISTS notification (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NULL,
    is_read BIT NULL,
    created_at DATETIME(6) NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    INDEX idx_notification_user_read_created (user_id, is_read, created_at),
    INDEX idx_notification_task (task_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notification_preference (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    notify_on_complete BIT NULL,
    notify_on_overdue BIT NULL,
    notify_on_cycle_reset BIT NULL,
    CONSTRAINT pk_notification_preference PRIMARY KEY (id),
    CONSTRAINT uk_notification_preference_user UNIQUE (user_id)
) ENGINE=InnoDB;
