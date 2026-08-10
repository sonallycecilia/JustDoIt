CREATE TABLE time_entry (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    seconds BIGINT NOT NULL,
    source VARCHAR(255) NULL,
    CONSTRAINT pk_time_entry PRIMARY KEY (id),
    CONSTRAINT fk_time_entry_task
        FOREIGN KEY (task_id) REFERENCES task (id),
    INDEX idx_time_entry_task (task_id),
    INDEX idx_time_entry_started (started_at)
) ENGINE=InnoDB;
