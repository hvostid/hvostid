-- V3: Add profile fields to users table and create user_roles join table

ALTER TABLE users
    ADD COLUMN phone VARCHAR(50);
ALTER TABLE users
    ADD COLUMN city VARCHAR(255);
ALTER TABLE users
    ADD COLUMN bio TEXT;
ALTER TABLE users
    ADD COLUMN rating DOUBLE PRECISION;

-- Create user_roles table for multiple roles per user
CREATE TABLE user_roles
(
    user_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles (user_id);

-- Migrate existing role data from users.role column to user_roles table
INSERT INTO user_roles (user_id, role)
SELECT id, UPPER(role)
FROM users;

-- Drop the old role column
ALTER TABLE users
    DROP COLUMN role;
