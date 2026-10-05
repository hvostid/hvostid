CREATE TABLE passport_listing_references (
 passport_id BIGINT PRIMARY KEY REFERENCES pet_passports(id) ON DELETE RESTRICT,
 listing_id BIGINT NOT NULL UNIQUE,
 approved BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE object_cleanup_jobs (
 id BIGSERIAL PRIMARY KEY, passport_id BIGINT, bucket VARCHAR(255) NOT NULL, storage_path VARCHAR(1024) NOT NULL,
 available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), attempts INT NOT NULL DEFAULT 0,
 UNIQUE(bucket, storage_path)
);

-- Tombstones fence out delayed requests after compensation, including after passport deletion.
CREATE TABLE passport_reference_revisions (
 listing_id BIGINT PRIMARY KEY, revision BIGINT NOT NULL
);

CREATE INDEX object_cleanup_jobs_available_idx ON object_cleanup_jobs(available_at);
