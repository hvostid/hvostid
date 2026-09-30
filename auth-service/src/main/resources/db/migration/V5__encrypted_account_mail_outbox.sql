CREATE TABLE account_mail_outbox (
    id BIGSERIAL PRIMARY KEY,
    account_token_id BIGINT NOT NULL UNIQUE REFERENCES account_tokens(id) ON DELETE CASCADE,
    encrypted_payload TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_account_mail_outbox_due ON account_mail_outbox(next_attempt_at);
