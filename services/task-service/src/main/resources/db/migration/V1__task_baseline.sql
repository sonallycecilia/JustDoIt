-- Baseline do domínio de tarefas.
-- As relações internas recebem foreign keys; user_id e referências a outros
-- serviços continuam sendo UUIDs lógicos, sem FK entre bounded contexts.

CREATE TABLE IF NOT EXISTS category (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT pk_category PRIMARY KEY (id),
    INDEX idx_category_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    series_id BINARY(16) NULL,
    category_id BINARY(16) NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    estimated_minutes INT NULL,
    due_date DATE NULL,
    due_time TIME(6) NULL,
    status VARCHAR(255) NULL,
    priority VARCHAR(255) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_task PRIMARY KEY (id),
    CONSTRAINT fk_task_category
        FOREIGN KEY (category_id) REFERENCES category (id),
    INDEX idx_task_user (user_id),
    INDEX idx_task_user_status_due (user_id, status, due_date),
    INDEX idx_task_series_status_due (series_id, status, due_date),
    INDEX idx_task_completed (user_id, completed_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS subtask (
    id BINARY(16) NOT NULL,
    parent_task_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(255) NULL,
    position INT NULL,
    CONSTRAINT pk_subtask PRIMARY KEY (id),
    CONSTRAINT fk_subtask_task
        FOREIGN KEY (parent_task_id) REFERENCES task (id),
    INDEX idx_subtask_task_position (parent_task_id, position)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_note (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    content TEXT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_task_note PRIMARY KEY (id),
    CONSTRAINT uk_task_note_task UNIQUE (task_id),
    CONSTRAINT fk_task_note_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS note (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    title VARCHAR(255) NULL,
    content TEXT NULL,
    pinned BIT NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    CONSTRAINT pk_note PRIMARY KEY (id),
    INDEX idx_note_user (user_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_timer (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    estimated_minutes INT NULL,
    actual_seconds BIGINT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_task_timer PRIMARY KEY (id),
    CONSTRAINT uk_task_timer_task UNIQUE (task_id),
    CONSTRAINT fk_task_timer_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS active_timer (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_active_timer PRIMARY KEY (id),
    CONSTRAINT uk_active_timer_user UNIQUE (user_id),
    INDEX idx_active_timer_task (task_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS focus_session (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    focus_minutes INT NULL,
    break_minutes INT NULL,
    session_type VARCHAR(255) NULL,
    started_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    completed BIT NULL,
    CONSTRAINT pk_focus_session PRIMARY KEY (id),
    CONSTRAINT fk_focus_session_task FOREIGN KEY (task_id) REFERENCES task (id),
    INDEX idx_focus_session_task_started (task_id, started_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS cycle_config (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    cycle_type VARCHAR(255) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    next_reset_date DATE NULL,
    interval_unit VARCHAR(255) NULL,
    interval_count INT NULL,
    total_occurrences INT NULL,
    start_time TIME(6) NULL,
    CONSTRAINT pk_cycle_config PRIMARY KEY (id),
    CONSTRAINT uk_cycle_config_task UNIQUE (task_id),
    CONSTRAINT fk_cycle_config_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_module_config (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    focus_enabled BIT NULL,
    cycle_enabled BIT NULL,
    priority_enabled BIT NULL,
    timer_enabled BIT NULL,
    notes_enabled BIT NULL,
    CONSTRAINT pk_task_module_config PRIMARY KEY (id),
    CONSTRAINT uk_task_module_config_task UNIQUE (task_id),
    CONSTRAINT fk_task_module_config_task FOREIGN KEY (task_id) REFERENCES task (id)
) ENGINE=InnoDB;
