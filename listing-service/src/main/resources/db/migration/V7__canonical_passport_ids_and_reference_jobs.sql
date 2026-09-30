UPDATE listings SET passport_id = CAST(CAST(regexp_replace(BTRIM(passport_id), '^passport-', '') AS NUMERIC) AS TEXT)
WHERE BTRIM(passport_id) ~ '^(passport-)?[0-9]+$';
CREATE TABLE passport_reference_jobs (
 id BIGSERIAL PRIMARY KEY, listing_id BIGINT NOT NULL, passport_id BIGINT NOT NULL, seller_id BIGINT NOT NULL,
 attempts INT NOT NULL DEFAULT 0, available_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Existing active references are reconciled before a passport mutation can succeed.
INSERT INTO passport_reference_jobs(listing_id,passport_id,seller_id)
SELECT id, CAST(passport_id AS BIGINT), seller_id FROM listings
WHERE status IN ('MODERATION','PUBLISHED')
AND (CASE WHEN passport_id ~ '^[1-9][0-9]{0,18}$' THEN CAST(passport_id AS NUMERIC) END) <= 9223372036854775807;

CREATE INDEX passport_reference_jobs_available_idx ON passport_reference_jobs(available_at);
