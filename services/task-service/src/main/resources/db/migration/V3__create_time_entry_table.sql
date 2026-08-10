CREATE TABLE time_entry (
    id BINARY(16) NOT NULL,
    task_id BINARY(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6),
    seconds BIGINT NOT NULL,
    source ENUM ('COMPLETION_ESTIMATE','LEGACY','MANUAL','TIMER'),
    CONSTRAINT pk_time_entry PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_time_entry_task ON time_entry (task_id);
CREATE INDEX idx_time_entry_started ON time_entry (started_at);

ALTER TABLE time_entry
    ADD CONSTRAINT fk_time_entry_task
    FOREIGN KEY (task_id) REFERENCES task (id);