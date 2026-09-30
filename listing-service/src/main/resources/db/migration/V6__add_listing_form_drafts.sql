-- Unfinished creation forms are private and never enter the listing lifecycle.
CREATE TABLE listing_form_drafts
(
    seller_id   BIGINT PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    species     VARCHAR(255) NOT NULL,
    breed       VARCHAR(255),
    age         INT,
    price       INT,
    city        VARCHAR(255) NOT NULL
);
