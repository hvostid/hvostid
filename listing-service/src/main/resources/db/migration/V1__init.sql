-- V1__create_listings_table.sql

CREATE TABLE listings (
    id          BIGSERIAL       PRIMARY KEY,
    seller_id   BIGINT          NOT NULL,
    title       VARCHAR(255)    NOT NULL,
    description VARCHAR(5000),
    species     VARCHAR(255)    NOT NULL,
    breed       VARCHAR(255),
    age         INT,
    price       INT,
    city        VARCHAR(255)    NOT NULL,
    status      VARCHAR(50)     NOT NULL,
    passport_id VARCHAR(255),
    created_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP       NOT NULL DEFAULT NOW()
);
