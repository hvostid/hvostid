-- V3__add_trust_score_and_moderated.sql
-- Cached trust score (0-100) on pet_passports plus the moderated flag
-- used by T15 trust-score calculation.

ALTER TABLE pet_passports
    ADD COLUMN IF NOT EXISTS trust_score INT     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS moderated   BOOLEAN NOT NULL DEFAULT FALSE;
