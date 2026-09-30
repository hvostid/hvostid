-- Refuse an ambiguous identity migration instead of silently merging accounts.
DO $$ BEGIN
    IF EXISTS (SELECT lower(trim(email)) FROM users GROUP BY lower(trim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Case-insensitive email duplicates must be resolved before account migration';
    END IF;
END $$;
UPDATE users SET email = lower(trim(email));
CREATE UNIQUE INDEX ux_users_normalized_email ON users (lower(email));
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN contact_sharing_enabled BOOLEAN NOT NULL DEFAULT FALSE;
-- Preserve existing opaque-token sessions while replacing recoverable credentials with digests.
UPDATE sessions SET access_token = encode(sha256(convert_to(access_token, 'UTF8')), 'hex'),
                    refresh_token = encode(sha256(convert_to(refresh_token, 'UTF8')), 'hex');
CREATE INDEX idx_sessions_refresh_expiry ON sessions (refresh_token_expires_at);
CREATE TABLE account_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_account_tokens_expiry ON account_tokens (expires_at);
CREATE UNIQUE INDEX ux_account_tokens_user_purpose ON account_tokens (user_id, purpose);
