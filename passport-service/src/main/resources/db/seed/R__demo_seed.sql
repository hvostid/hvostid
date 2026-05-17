-- Repeatable demo seed for passport-service (profile: demo)

DELETE FROM passport_documents
WHERE passport_id BETWEEN 1 AND 99;

DELETE FROM vaccinations
WHERE passport_id BETWEEN 1 AND 99;

DELETE FROM pet_passports
WHERE id BETWEEN 1 AND 99;

INSERT INTO pet_passports (
    id, seller_id, species, breed, name, birth_date, gender, color, temperament,
    special_needs, neutered, microchipped, trust_score, moderated, created_at, updated_at)
VALUES
    (1, 3, 'Dog', 'Labrador', 'Buddy', '2023-03-15', 'MALE', 'Golden', 'Friendly and playful', NULL, TRUE, TRUE, 85, TRUE, NOW(), NOW()),
    (2, 4, 'Cat', 'British Shorthair', 'Mila', '2022-11-01', 'FEMALE', 'Gray', 'Calm indoor cat', NULL, TRUE, TRUE, 70, TRUE, NOW(), NOW()),
    (3, 5, 'Dog', 'French Bulldog', 'Rocky', '2024-01-20', 'MALE', 'Fawn', 'Affectionate couch potato', 'Needs shade in summer', TRUE, FALSE, 55, FALSE, NOW(), NOW()),
    (4, 6, 'Bird', 'Budgerigar', 'Kiwi', '2024-06-10', 'UNKNOWN', 'Green', 'Sings in the morning', NULL, FALSE, FALSE, 25, FALSE, NOW(), NOW()),
    (5, 7, 'Dog', 'Border Collie', 'Luna', '2021-08-05', 'FEMALE', 'Black-white', 'Very active, loves fetch', NULL, TRUE, TRUE, 95, TRUE, NOW(), NOW()),
    (6, 8, 'Cat', 'Maine Coon', 'Thor', '2020-05-12', 'MALE', 'Brown tabby', 'Gentle giant', NULL, TRUE, TRUE, 90, TRUE, NOW(), NOW()),
    (7, 3, 'Dog', 'Beagle', 'Charlie', '2023-09-22', 'MALE', 'Tricolor', 'Curious sniffer', NULL, FALSE, TRUE, 40, FALSE, NOW(), NOW()),
    (8, 4, 'Cat', 'Siamese', 'Siam', '2023-02-14', 'FEMALE', 'Cream', 'Vocal and social', NULL, TRUE, TRUE, 65, TRUE, NOW(), NOW()),
    (9, 5, 'Dog', 'Poodle', 'Coco', '2022-12-30', 'FEMALE', 'Apricot', 'Hypoallergenic coat', 'Regular grooming', TRUE, TRUE, 80, TRUE, NOW(), NOW()),
    (10, 6, 'Reptile', 'Leopard Gecko', 'Spot', '2023-07-07', 'UNKNOWN', 'Yellow', 'Nocturnal, easy care', 'Terrarium required', FALSE, FALSE, 10, FALSE, NOW(), NOW()),
    (11, 7, 'Dog', 'Husky', 'Snow', '2021-04-18', 'MALE', 'Gray-white', 'High energy', 'Cold climate preferred', TRUE, TRUE, 75, TRUE, NOW(), NOW()),
    (12, 8, 'Cat', 'Scottish Fold', 'Foldik', '2024-02-28', 'MALE', 'White', 'Quiet companion', NULL, FALSE, FALSE, 45, FALSE, NOW(), NOW()),
    (13, 3, 'Dog', 'Corgi', 'Biscuit', '2023-11-11', 'MALE', 'Red', 'Short legs, big heart', NULL, TRUE, TRUE, 72, TRUE, NOW(), NOW()),
    (14, 4, 'Cat', 'Persian', 'Fluffy', '2021-10-03', 'FEMALE', 'White', 'Needs daily brushing', 'Indoor only', TRUE, TRUE, 68, TRUE, NOW(), NOW()),
    (15, 5, 'Dog', 'German Shepherd', 'Rex', '2020-12-25', 'MALE', 'Black-tan', 'Loyal guardian', NULL, TRUE, TRUE, 88, TRUE, NOW(), NOW()),
    (16, 6, 'Bird', 'Cockatiel', 'Pip', '2024-04-01', 'MALE', 'Yellow', 'Whistles tunes', NULL, FALSE, FALSE, 30, FALSE, NOW(), NOW()),
    (17, 7, 'Dog', 'Dachshund', 'Sausage', '2022-06-16', 'MALE', 'Brown', 'Brave little hunter', 'Watch back strain', TRUE, TRUE, 60, FALSE, NOW(), NOW()),
    (18, 8, 'Cat', 'Bengal', 'Leo', '2023-05-09', 'MALE', 'Spotted', 'Likes water play', NULL, TRUE, TRUE, 77, TRUE, NOW(), NOW()),
    (19, 3, 'Dog', 'Golden Retriever', 'Sunny', '2022-01-30', 'FEMALE', 'Golden', 'Great with kids', NULL, TRUE, TRUE, 92, TRUE, NOW(), NOW()),
    (20, 4, 'Cat', 'Ragdoll', 'Cloud', '2023-08-17', 'FEMALE', 'Blue point', 'Relaxed lap cat', NULL, TRUE, TRUE, 74, TRUE, NOW(), NOW()),
    (21, 5, 'Dog', 'Chihuahua', 'Peanut', '2024-03-08', 'MALE', 'Tan', 'Tiny but bold', NULL, FALSE, FALSE, 35, FALSE, NOW(), NOW()),
    (22, 6, 'Rabbit', 'Dwarf', 'Cotton', '2023-12-12', 'FEMALE', 'White', 'Gentle and litter-trained', NULL, TRUE, FALSE, 50, FALSE, NOW(), NOW()),
    (23, 7, 'Dog', 'Akita', 'Hachi', '2019-09-09', 'MALE', 'Red', 'Independent spirit', 'Experienced owner', TRUE, TRUE, 82, TRUE, NOW(), NOW()),
    (24, 8, 'Cat', 'Sphynx', 'Nude', '2022-04-22', 'MALE', 'Pink', 'Warm-seeking', 'Sweater in winter', TRUE, TRUE, 58, FALSE, NOW(), NOW()),
    (25, 3, 'Dog', 'Maltese', 'Snowball', '2024-05-05', 'FEMALE', 'White', 'Small apartment friendly', NULL, FALSE, TRUE, 48, FALSE, NOW(), NOW()),
    (26, 4, 'Cat', 'Norwegian Forest', 'Forest', '2021-07-19', 'MALE', 'Brown', 'Climber', NULL, TRUE, TRUE, 71, TRUE, NOW(), NOW()),
    (27, 5, 'Dog', 'Boxer', 'Bruno', '2020-11-30', 'MALE', 'Fawn', 'Playful boxer', NULL, TRUE, TRUE, 86, TRUE, NOW(), NOW()),
    (28, 6, 'Bird', 'Canary', 'Goldie', '2024-08-14', 'FEMALE', 'Yellow', 'Sweet singer', NULL, FALSE, FALSE, 20, FALSE, NOW(), NOW()),
    (29, 7, 'Dog', 'Shiba Inu', 'Taro', '2022-10-10', 'MALE', 'Red', 'Cat-like dog', NULL, TRUE, TRUE, 79, TRUE, NOW(), NOW()),
    (30, 8, 'Cat', 'Russian Blue', 'Smoky', '2023-01-25', 'FEMALE', 'Blue-gray', 'Shy at first', NULL, TRUE, TRUE, 67, TRUE, NOW(), NOW()),
    (31, 3, 'Dog', 'Samoyed', 'Cloudy', '2021-02-02', 'FEMALE', 'White', 'Smiling fluffball', 'Heavy shedding', TRUE, TRUE, 91, TRUE, NOW(), NOW()),
    (32, 4, 'Cat', 'Abyssinian', 'Amber', '2023-10-15', 'FEMALE', 'Ruddy', 'Athletic explorer', NULL, TRUE, TRUE, 63, TRUE, NOW(), NOW());

INSERT INTO vaccinations (passport_id, name, date, next_date, verified)
VALUES
    (1, 'Rabies', '2024-01-10', '2025-01-10', TRUE),
    (1, 'DHPP', '2024-02-15', '2025-02-15', TRUE),
    (2, 'FVRCP', '2023-12-01', '2024-12-01', TRUE),
    (5, 'Rabies', '2023-06-01', '2024-06-01', TRUE),
    (5, 'DHPP', '2023-07-01', '2024-07-01', TRUE),
    (5, 'Bordetella', '2024-03-01', '2025-03-01', TRUE),
    (9, 'Rabies', '2023-11-20', '2024-11-20', TRUE),
    (15, 'Rabies', '2023-01-05', '2024-01-05', TRUE),
    (15, 'DHPP', '2023-02-05', '2024-02-05', TRUE),
    (19, 'Rabies', '2022-08-01', '2023-08-01', TRUE),
    (23, 'Rabies', '2023-04-10', '2024-04-10', TRUE),
    (27, 'Rabies', '2023-09-15', '2024-09-15', TRUE),
    (31, 'Rabies', '2023-03-20', '2024-03-20', TRUE);

INSERT INTO passport_documents (passport_id, type, original_filename, storage_path, mime_type, size, uploaded_at)
VALUES
    (1, 'PHOTO', 'buddy.jpg', '3/1/demo-photo-1.jpg', 'image/jpeg', 2048, NOW()),
    (1, 'VACCINATION_CERT', 'buddy-vaccines.pdf', '3/1/demo-vet-cert-1.pdf', 'application/pdf', 4096, NOW()),
    (2, 'PHOTO', 'mila.jpg', '4/2/demo-photo-2.jpg', 'image/jpeg', 2048, NOW()),
    (3, 'PHOTO', 'rocky.jpg', '5/3/demo-photo-3.jpg', 'image/jpeg', 2048, NOW()),
    (4, 'PHOTO', 'kiwi.jpg', '6/4/demo-photo-4.jpg', 'image/jpeg', 2048, NOW()),
    (5, 'PHOTO', 'luna.jpg', '7/5/demo-photo-5.jpg', 'image/jpeg', 2048, NOW()),
    (5, 'VET_RECORD', 'luna-vet.pdf', '7/5/demo-vet-record-5.pdf', 'application/pdf', 3072, NOW()),
    (6, 'PHOTO', 'thor.jpg', '8/6/demo-photo-6.jpg', 'image/jpeg', 2048, NOW()),
    (7, 'PHOTO', 'charlie.jpg', '3/7/demo-photo-7.jpg', 'image/jpeg', 2048, NOW()),
    (8, 'PHOTO', 'siam.jpg', '4/8/demo-photo-8.jpg', 'image/jpeg', 2048, NOW()),
    (9, 'PHOTO', 'coco.jpg', '5/9/demo-photo-9.jpg', 'image/jpeg', 2048, NOW()),
    (10, 'PHOTO', 'spot.jpg', '6/10/demo-photo-10.jpg', 'image/jpeg', 2048, NOW()),
    (11, 'PHOTO', 'snow.jpg', '7/11/demo-photo-11.jpg', 'image/jpeg', 2048, NOW()),
    (12, 'PHOTO', 'foldik.jpg', '8/12/demo-photo-12.jpg', 'image/jpeg', 2048, NOW()),
    (15, 'PHOTO', 'rex.jpg', '5/15/demo-photo-15.jpg', 'image/jpeg', 2048, NOW()),
    (15, 'VACCINATION_CERT', 'rex-vaccines.pdf', '5/15/demo-vet-cert-15.pdf', 'application/pdf', 4096, NOW()),
    (19, 'PHOTO', 'sunny.jpg', '3/19/demo-photo-19.jpg', 'image/jpeg', 2048, NOW()),
    (23, 'PHOTO', 'hachi.jpg', '7/23/demo-photo-23.jpg', 'image/jpeg', 2048, NOW()),
    (27, 'PHOTO', 'bruno.jpg', '5/27/demo-photo-27.jpg', 'image/jpeg', 2048, NOW()),
    (31, 'PHOTO', 'cloudy.jpg', '3/31/demo-photo-31.jpg', 'image/jpeg', 2048, NOW());

-- Bump past the demo-reserved range so anything created via the UI lands at id >= 100.
SELECT setval('pet_passports_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM pet_passports), 99));
SELECT setval('vaccinations_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM vaccinations), 99));
SELECT setval('passport_documents_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM passport_documents), 99));

DO $$
DECLARE
    passport_count INT;
BEGIN
    SELECT COUNT(*) INTO passport_count FROM pet_passports WHERE id BETWEEN 1 AND 99;
    IF passport_count < 30 THEN
        RAISE EXCEPTION 'Demo seed failed: expected at least 30 demo passports, got %', passport_count;
    END IF;
END $$;
