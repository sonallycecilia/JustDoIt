-- Criação do domínio de fechamento semanal (Weekly Closure)

CREATE TABLE IF NOT EXISTS weekly_cycles (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    start_date DATETIME(6) NOT NULL,
    end_date DATETIME(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_weekly_cycles PRIMARY KEY (id),
    INDEX idx_weekly_cycles_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS weekly_task_snapshots (
    id BINARY(16) NOT NULL,
    cycle_id BINARY(16) NOT NULL,
    original_task_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status_at_closure VARCHAR(255) NOT NULL,
    points INT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_weekly_task_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_task_snapshot_cycle 
        FOREIGN KEY (cycle_id) REFERENCES weekly_cycles (id) ON DELETE RESTRICT,
    INDEX idx_task_snapshot_cycle (cycle_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS weekly_time_entry_snapshots (
    id BINARY(16) NOT NULL,
    cycle_id BINARY(16) NOT NULL,
    original_task_id BINARY(16) NOT NULL,
    time_logged_minutes INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_weekly_time_entry_snapshots PRIMARY KEY (id),
    CONSTRAINT fk_time_snapshot_cycle 
        FOREIGN KEY (cycle_id) REFERENCES weekly_cycles (id) ON DELETE RESTRICT,
    INDEX idx_time_snapshot_cycle (cycle_id)
) ENGINE=InnoDB;


ALTER TABLE task ADD COLUMN cycle_id BINARY(16) NULL;
CREATE INDEX idx_task_cycle ON task (cycle_id);