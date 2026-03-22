-- V1: Create users and sessions tables for auth service

CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'buyer',
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users (email);

CREATE TABLE sessions
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    access_token  VARCHAR(255) NOT NULL UNIQUE,
    refresh_token VARCHAR(255) NOT NULL UNIQUE,
    expires_at    TIMESTAMP    NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_access_token ON sessions (access_token);
CREATE INDEX idx_sessions_refresh_token ON sessions (refresh_token);
CREATE INDEX idx_sessions_user_id ON sessions (user_id);
CREATE INDEX idx_sessions_expires_at ON sessions (expires_at);
