CREATE TABLE pet_passports
(
    id            BIGSERIAL PRIMARY KEY,
    seller_id     BIGINT       NOT NULL,
    species       VARCHAR(255) NOT NULL,
    breed         VARCHAR(255),
    name          VARCHAR(255) NOT NULL,
    birth_date    DATE         NOT NULL,
    gender        VARCHAR(50)  NOT NULL,
    color         VARCHAR(255),
    temperament   VARCHAR(1000),
    special_needs VARCHAR(1000),
    neutered      BOOLEAN      NOT NULL,
    microchipped  BOOLEAN      NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pet_passports_seller_id ON pet_passports (seller_id);

CREATE TABLE vaccinations
(
    id          BIGSERIAL PRIMARY KEY,
    passport_id BIGINT       NOT NULL REFERENCES pet_passports (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    date        DATE         NOT NULL,
    next_date   DATE,
    verified    BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_vaccinations_passport_id ON vaccinations (passport_id);
