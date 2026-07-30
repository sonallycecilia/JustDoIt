-- Baseline do domínio de agenda.

CREATE TABLE IF NOT EXISTS time_block (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    task_id BINARY(16) NULL,
    start_date_time DATETIME(6) NOT NULL,
    end_date_time DATETIME(6) NOT NULL,
    estimated_minutes INT NULL,
    date DATE NOT NULL,
    CONSTRAINT pk_time_block PRIMARY KEY (id),
    INDEX idx_time_block_user_date (user_id, date),
    INDEX idx_time_block_task (task_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS weekly_plan (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    status VARCHAR(255) NULL,
    CONSTRAINT pk_weekly_plan PRIMARY KEY (id),
    INDEX idx_weekly_plan_user_dates (user_id, week_start_date, week_end_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS weekly_summary (
    id BINARY(16) NOT NULL,
    weekly_plan_id BINARY(16) NOT NULL,
    total_estimated_minutes INT NULL,
    total_actual_seconds BIGINT NULL,
    deviation_seconds BIGINT NULL,
    completed_tasks INT NULL,
    total_tasks INT NULL,
    CONSTRAINT pk_weekly_summary PRIMARY KEY (id),
    CONSTRAINT uk_weekly_summary_plan UNIQUE (weekly_plan_id),
    CONSTRAINT fk_weekly_summary_plan
        FOREIGN KEY (weekly_plan_id) REFERENCES weekly_plan (id)
) ENGINE=InnoDB;
