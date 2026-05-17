-- V5__add_sold_at_and_seller_index.sql
-- T09: mark when a listing was sold and speed up "my listings" filters.

ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS sold_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_listings_seller_id ON listings (seller_id);
CREATE INDEX IF NOT EXISTS idx_listings_seller_status ON listings (seller_id, status);
