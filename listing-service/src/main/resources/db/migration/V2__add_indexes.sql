-- V2__indexes_and_constraints.sql

-- quick search for published
CREATE INDEX idx_listings_status ON listings(status);

-- duplicate protection
CREATE UNIQUE INDEX ux_listing_unique_active
    ON listings (seller_id, LOWER(title))
    WHERE status != 'ARCHIVED';