-- Repeatable demo seed for matching-service (profile: demo)

DELETE FROM buyer_questionnaire
WHERE user_id BETWEEN 1 AND 99;

INSERT INTO buyer_questionnaire (
    id, user_id, living_space, living_area, has_yard, has_children, children_age_min,
    has_allergies, allergy_details, pet_experience, activity_level, monthly_budget,
    work_schedule, ready_for_adaptation, preferred_species, preferred_breed, created_at, updated_at)
VALUES
    (1, 9, 'APARTMENT', 65, FALSE, FALSE, NULL, FALSE, NULL, 'BEGINNER', 'MEDIUM', 15000, 'HYBRID', TRUE, 'Cat', 'British Shorthair', NOW(), NOW()),
    (2, 10, 'HOUSE', 120, TRUE, TRUE, 8, FALSE, NULL, 'NONE', 'LOW', 12000, 'HOME', TRUE, 'Dog', 'Labrador', NOW(), NOW()),
    (3, 11, 'APARTMENT', 80, FALSE, TRUE, 5, TRUE, 'Mild cat allergy', 'EXPERIENCED', 'MEDIUM', 20000, 'OFFICE', TRUE, 'Dog', 'Poodle', NOW(), NOW()),
    (4, 12, 'HOUSE', 150, TRUE, FALSE, NULL, FALSE, NULL, 'EXPERIENCED', 'HIGH', 25000, 'HYBRID', TRUE, 'Dog', 'Border Collie', NOW(), NOW()),
    (5, 13, 'APARTMENT', 55, FALSE, FALSE, NULL, TRUE, 'Feather allergy', 'BEGINNER', 'LOW', 8000, 'OFFICE', FALSE, 'Cat', NULL, NOW(), NOW()),
    (6, 14, 'FARM', 200, TRUE, FALSE, NULL, FALSE, NULL, 'PROFESSIONAL', 'VERY_HIGH', 30000, 'HOME', TRUE, 'Dog', 'Husky', NOW(), NOW());

SELECT setval('buyer_questionnaire_id_seq', (SELECT COALESCE(MAX(id), 1) FROM buyer_questionnaire));

DO $$
DECLARE
    questionnaire_count INT;
BEGIN
    SELECT COUNT(*) INTO questionnaire_count FROM buyer_questionnaire WHERE user_id BETWEEN 9 AND 14;
    IF questionnaire_count < 5 THEN
        RAISE EXCEPTION 'Demo seed failed: expected at least 5 demo questionnaires, got %', questionnaire_count;
    END IF;
END $$;
