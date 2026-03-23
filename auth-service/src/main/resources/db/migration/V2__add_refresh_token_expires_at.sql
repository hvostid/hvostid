-- V2: Add refresh_token_expires_at to sessions table

ALTER TABLE sessions
    ADD COLUMN refresh_token_expires_at TIMESTAMP NOT NULL DEFAULT now();

-- Backfill existing sessions: set refresh expiry to created_at + 7 days
UPDATE sessions
SET refresh_token_expires_at = created_at + INTERVAL '7 days'
WHERE refresh_token_expires_at = now();

-- Remove the default after backfill
ALTER TABLE sessions
    ALTER COLUMN refresh_token_expires_at DROP DEFAULT;
