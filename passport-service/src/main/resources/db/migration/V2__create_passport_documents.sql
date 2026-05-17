CREATE TABLE passport_documents
(
    id                BIGSERIAL PRIMARY KEY,
    passport_id       BIGINT       NOT NULL REFERENCES pet_passports (id) ON DELETE CASCADE,
    type              VARCHAR(50)  NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    storage_path      VARCHAR(1000) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    size              BIGINT       NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_passport_documents_passport_id ON passport_documents (passport_id);
CREATE INDEX idx_passport_documents_type ON passport_documents (type);
