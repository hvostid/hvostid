-- Add moderation_comment column to listings table
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
    changed_at TIMESTAMP NOT NULL DEFAULT NOW()
    );

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_listing_status_history_listing_id
    ON listing_status_history(listing_id);

CREATE INDEX IF NOT EXISTS idx_listing_status_history_changed_at
    ON listing_status_history(changed_at);
