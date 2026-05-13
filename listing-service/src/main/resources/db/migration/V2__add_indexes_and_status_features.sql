-- V2__add_indexes_and_status_features.sql

-- =====================================================
-- Part 1: Indexes and constraints (from original V2)
-- =====================================================

-- Quick search for published listings
CREATE INDEX IF NOT EXISTS idx_listings_status ON listings (status);

-- Duplicate protection: only one active listing per seller with same title
CREATE UNIQUE INDEX IF NOT EXISTS ux_listing_unique_active
    ON listings (seller_id, LOWER(title))
    WHERE status != 'ARCHIVED';

-- =====================================================
-- Part 2: Status transition features (new)
-- =====================================================

-- Add moderation comment column
ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS moderation_comment VARCHAR(500);

-- Create listing status history table
CREATE TABLE IF NOT EXISTS listing_status_history (
                                                      id BIGSERIAL PRIMARY KEY,
                                                      listing_id BIGINT NOT NULL,
                                                      from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    changed_by_role VARCHAR(50),
    comment VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Create indexes for history table
CREATE INDEX IF NOT EXISTS idx_listing_status_history_listing_id
    ON listing_status_history(listing_id);

CREATE INDEX IF NOT EXISTS idx_listing_status_history_changed_at
    ON listing_status_history(changed_at);
