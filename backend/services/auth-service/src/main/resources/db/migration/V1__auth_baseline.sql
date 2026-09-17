-- Baseline do domínio de autenticação.
-- IF NOT EXISTS permite adotar o Flyway sobre instalações anteriormente
-- mantidas pelo Hibernate ddl-auto=update sem apagar dados existentes.

CREATE TABLE IF NOT EXISTS users (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    birth_date DATE NULL,
    avatar_url MEDIUMTEXT NULL,
    created_at DATETIME(6) NULL,
    active BIT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refresh_token (
    id BINARY(16) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    user_id BINARY(16) NOT NULL,
    email VARCHAR(255) NOT NULL,
    profile VARCHAR(255) NULL,
    expires_at DATETIME(6) NOT NULL,
    remember_me BIT NOT NULL,
    used_at DATETIME(6) NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_user (user_id),
    INDEX idx_refresh_token_expires (expires_at)
) ENGINE=InnoDB;
