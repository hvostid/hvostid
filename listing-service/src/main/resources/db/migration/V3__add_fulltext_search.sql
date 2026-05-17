-- V3__add_fulltext_search.sql
-- Two-column approach: Russian morphology + simple fallback

ALTER TABLE listings
    ADD COLUMN IF NOT EXISTS search_vector_ru tsvector,
    ADD COLUMN IF NOT EXISTS search_vector_en tsvector;

CREATE INDEX IF NOT EXISTS idx_listings_search_vector_ru
    ON listings USING GIN(search_vector_ru);

CREATE INDEX IF NOT EXISTS idx_listings_search_vector_en
    ON listings USING GIN(search_vector_en);

CREATE OR REPLACE FUNCTION listings_search_vector_update()
RETURNS TRIGGER AS $$
BEGIN
    -- Russian morphology (stemming)
    NEW.search_vector_ru :=
        setweight(to_tsvector('russian', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('russian', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('russian', COALESCE(NEW.breed, '')), 'C');

    -- Simple config for English and exact matches
    NEW.search_vector_en :=
        setweight(to_tsvector('simple', COALESCE(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(NEW.description, '')), 'B') ||
        setweight(to_tsvector('simple', COALESCE(NEW.breed, '')), 'C');

RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_listings_search_vector_update ON listings;
CREATE TRIGGER trigger_listings_search_vector_update
    BEFORE INSERT OR UPDATE OF title, description, breed
                     ON listings
                         FOR EACH ROW
                         EXECUTE FUNCTION listings_search_vector_update();

UPDATE listings
SET
    search_vector_ru =
        setweight(to_tsvector('russian', COALESCE(title, '')), 'A') ||
        setweight(to_tsvector('russian', COALESCE(description, '')), 'B') ||
        setweight(to_tsvector('russian', COALESCE(breed, '')), 'C'),
    search_vector_en =
        setweight(to_tsvector('simple', COALESCE(title, '')), 'A') ||
        setweight(to_tsvector('simple', COALESCE(description, '')), 'B') ||
        setweight(to_tsvector('simple', COALESCE(breed, '')), 'C')
WHERE search_vector_ru IS NULL OR search_vector_en IS NULL;
