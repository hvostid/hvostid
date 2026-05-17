-- V4__add_listing_flags.sql
-- Adds the listing_flags table that backs T16 (problem-listing reports).

CREATE TABLE IF NOT EXISTS listing_flags (
    id          BIGSERIAL PRIMARY KEY,
    listing_id  BIGINT        NOT NULL,
    reporter_id BIGINT        NOT NULL,
    reason      VARCHAR(50)   NOT NULL,
    description VARCHAR(1000),
    status      VARCHAR(50)   NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_listing_flags_listing_reporter UNIQUE (listing_id, reporter_id)
);

CREATE INDEX IF NOT EXISTS idx_listing_flags_listing_id ON listing_flags (listing_id);
CREATE INDEX IF NOT EXISTS idx_listing_flags_status ON listing_flags (status);
