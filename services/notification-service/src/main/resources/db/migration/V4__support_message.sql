CREATE TABLE support_message (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    sender VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    page_url VARCHAR(2048) NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_support_message PRIMARY KEY (id),
    INDEX idx_support_message_user_created (user_id, created_at)
) ENGINE=InnoDB;
