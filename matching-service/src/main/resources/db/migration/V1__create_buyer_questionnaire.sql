-- V1: Create buyer_questionnaire table for matching service

CREATE TABLE buyer_questionnaire
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT      NOT NULL UNIQUE,
    living_space         VARCHAR(50) NOT NULL,
    living_area          INT         NOT NULL,
    has_yard             BOOLEAN     NOT NULL,
    has_children         BOOLEAN     NOT NULL,
    children_age_min     INT,
    has_allergies        BOOLEAN     NOT NULL,
    allergy_details      VARCHAR(2000),
    pet_experience       VARCHAR(50) NOT NULL,
    activity_level       VARCHAR(50) NOT NULL,
    monthly_budget       INT         NOT NULL,
    work_schedule        VARCHAR(50) NOT NULL,
    ready_for_adaptation BOOLEAN     NOT NULL,
    preferred_species    VARCHAR(255),
    preferred_breed      VARCHAR(255),
    created_at           TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP   NOT NULL DEFAULT now()
);
