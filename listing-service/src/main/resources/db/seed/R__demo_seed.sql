-- Repeatable demo seed for listing-service (profile: demo)

DELETE FROM listing_flags
WHERE listing_id BETWEEN 1 AND 99;

DELETE FROM listing_status_history
WHERE listing_id BETWEEN 1 AND 99;

DELETE FROM listings
WHERE id BETWEEN 1 AND 99;

INSERT INTO listings (
    id, seller_id, title, description, species, breed, age, price, city, status,
    passport_id, created_at, updated_at, moderation_comment, sold_at)
VALUES
    -- PUBLISHED (catalog)
    (1, 3, 'Friendly Labrador Buddy', 'Well-socialized lab puppy from a trusted home.', 'Dog', 'Labrador', 12, 45000, 'Moscow', 'PUBLISHED', '1', NOW() - INTERVAL '30 days', NOW(), NULL, NULL),
    (2, 4, 'Calm British Shorthair Mila', 'Indoor cat, litter trained, vaccinated.', 'Cat', 'British Shorthair', 18, 28000, 'Saint Petersburg', 'PUBLISHED', '2', NOW() - INTERVAL '25 days', NOW(), NULL, NULL),
    (3, 5, 'French Bulldog Rocky', 'Playful bulldog, great for apartments.', 'Dog', 'French Bulldog', 8, 62000, 'Kazan', 'PUBLISHED', '3', NOW() - INTERVAL '20 days', NOW(), NULL, NULL),
    (4, 6, 'Singing Budgerigar Kiwi', 'Colorful budgie with cage included.', 'Bird', 'Budgerigar', 3, 3500, 'Novosibirsk', 'PUBLISHED', '4', NOW() - INTERVAL '15 days', NOW(), NULL, NULL),
    (5, 7, 'Active Border Collie Luna', 'Needs an active family and daily walks.', 'Dog', 'Border Collie', 24, 38000, 'Yekaterinburg', 'PUBLISHED', '5', NOW() - INTERVAL '14 days', NOW(), NULL, NULL),
    (6, 8, 'Maine Coon Thor', 'Large gentle cat, neutered.', 'Cat', 'Maine Coon', 36, 42000, 'Sochi', 'PUBLISHED', '6', NOW() - INTERVAL '12 days', NOW(), NULL, NULL),
    (7, 3, 'Beagle Charlie for adoption', 'Curious beagle, loves kids.', 'Dog', 'Beagle', 10, 32000, 'Moscow', 'PUBLISHED', '7', NOW() - INTERVAL '11 days', NOW(), NULL, NULL),
    (8, 4, 'Siamese Siam vocal cat', 'Talkative siamese, very social.', 'Cat', 'Siamese', 14, 25000, 'Saint Petersburg', 'PUBLISHED', '8', NOW() - INTERVAL '10 days', NOW(), NULL, NULL),
    (9, 5, 'Hypoallergenic Poodle Coco', 'Apricot poodle with grooming history.', 'Dog', 'Poodle', 16, 55000, 'Kazan', 'PUBLISHED', '9', NOW() - INTERVAL '9 days', NOW(), NULL, NULL),
    (10, 6, 'Leopard Gecko Spot', 'Easy beginner reptile with terrarium.', 'Reptile', 'Leopard Gecko', 6, 8000, 'Novosibirsk', 'PUBLISHED', '10', NOW() - INTERVAL '8 days', NOW(), NULL, NULL),
    (11, 7, 'Husky Snow adventure dog', 'High-energy husky for experienced owners.', 'Dog', 'Husky', 30, 40000, 'Yekaterinburg', 'PUBLISHED', '11', NOW() - INTERVAL '7 days', NOW(), NULL, NULL),
    (12, 8, 'Scottish Fold Foldik', 'Quiet fold kitten, indoor only.', 'Cat', 'Scottish Fold', 5, 48000, 'Sochi', 'PUBLISHED', '12', NOW() - INTERVAL '6 days', NOW(), NULL, NULL),
    (33, 3, 'Golden Retriever Sunny', 'Family-friendly golden, great with children.', 'Dog', 'Golden Retriever', 20, 50000, 'Moscow', 'PUBLISHED', '19', NOW() - INTERVAL '5 days', NOW(), NULL, NULL),
    (34, 4, 'Ragdoll Cloud lap cat', 'Relaxed ragdoll, loves cuddles.', 'Cat', 'Ragdoll', 9, 36000, 'Saint Petersburg', 'PUBLISHED', '20', NOW() - INTERVAL '4 days', NOW(), NULL, NULL),
    (35, 5, 'German Shepherd Rex guard', 'Trained shepherd, loyal protector.', 'Dog', 'German Shepherd', 42, 58000, 'Kazan', 'PUBLISHED', '15', NOW() - INTERVAL '3 days', NOW(), NULL, NULL),
    -- DRAFT
    (15, 3, 'Corgi Biscuit draft', 'Corgi listing in preparation.', 'Dog', 'Corgi', 7, 40000, 'Moscow', 'DRAFT', '13', NOW() - INTERVAL '2 days', NOW(), NULL, NULL),
    (16, 4, 'Persian Fluffy draft', 'Long-haired persian, photos pending.', 'Cat', 'Persian', 28, 30000, 'Saint Petersburg', 'DRAFT', '14', NOW() - INTERVAL '2 days', NOW(), NULL, NULL),
    (17, 5, 'Chihuahua Peanut draft', 'Tiny chihuahua, still drafting text.', 'Dog', 'Chihuahua', 4, 22000, 'Kazan', 'DRAFT', '21', NOW() - INTERVAL '1 day', NOW(), NULL, NULL),
    (18, 6, 'Dwarf Rabbit Cotton draft', 'Gentle rabbit for calm homes.', 'Rabbit', 'Dwarf', 6, 5000, 'Novosibirsk', 'DRAFT', '22', NOW(), NOW(), NULL, NULL),
    (19, 7, 'Akita Hachi draft listing', 'Independent akita, draft only.', 'Dog', 'Akita', 48, 65000, 'Yekaterinburg', 'DRAFT', '23', NOW(), NOW(), NULL, NULL),
    -- MODERATION
    (20, 8, 'Sphynx Nude awaiting review', 'Hairless cat awaiting moderator.', 'Cat', 'Sphynx', 22, 52000, 'Sochi', 'MODERATION', '24', NOW() - INTERVAL '3 days', NOW(), NULL, NULL),
    (21, 3, 'Maltese Snowball in moderation', 'Small maltese submitted for review.', 'Dog', 'Maltese', 3, 35000, 'Moscow', 'MODERATION', '25', NOW() - INTERVAL '2 days', NOW(), NULL, NULL),
    (22, 4, 'Norwegian Forest Forest mod', 'Forest cat pending approval.', 'Cat', 'Norwegian Forest', 32, 38000, 'Saint Petersburg', 'MODERATION', '26', NOW() - INTERVAL '2 days', NOW(), NULL, NULL),
    (23, 5, 'Boxer Bruno moderation queue', 'Playful boxer waiting for moderator.', 'Dog', 'Boxer', 40, 44000, 'Kazan', 'MODERATION', '27', NOW() - INTERVAL '1 day', NOW(), NULL, NULL),
    (24, 6, 'Canary Goldie moderation', 'Sweet canary in review.', 'Bird', 'Canary', 2, 2500, 'Novosibirsk', 'MODERATION', '28', NOW(), NOW(), NULL, NULL),
    -- REJECTED
    (25, 7, 'Shiba Taro rejected listing', 'Rejected due to incomplete passport.', 'Dog', 'Shiba Inu', 18, 47000, 'Yekaterinburg', 'REJECTED', '29', NOW() - INTERVAL '10 days', NOW(), 'Poor quality photos', NULL),
    (26, 8, 'Russian Blue Smoky rejected', 'Rejected: misleading description.', 'Cat', 'Russian Blue', 12, 33000, 'Sochi', 'REJECTED', '30', NOW() - INTERVAL '8 days', NOW(), 'Description mismatch', NULL),
    (27, 3, 'Samoyed Cloudy rejected once', 'Needs better vaccination docs.', 'Dog', 'Samoyed', 34, 72000, 'Moscow', 'REJECTED', '31', NOW() - INTERVAL '5 days', NOW(), 'Missing vet documents', NULL),
    -- ARCHIVED
    (28, 4, 'Abyssinian Amber archived', 'Previously published, now archived.', 'Cat', 'Abyssinian', 11, 29000, 'Saint Petersburg', 'ARCHIVED', '32', NOW() - INTERVAL '60 days', NOW(), NULL, NULL),
    (29, 5, 'Cockatiel Pip archived', 'Owner paused the sale.', 'Bird', 'Cockatiel', 4, 4000, 'Kazan', 'ARCHIVED', '16', NOW() - INTERVAL '45 days', NOW(), NULL, NULL),
    (30, 6, 'Dachshund Sausage archived', 'Archived after seasonal pause.', 'Dog', 'Dachshund', 26, 31000, 'Novosibirsk', 'ARCHIVED', '17', NOW() - INTERVAL '40 days', NOW(), NULL, NULL),
    -- SOLD
    (31, 7, 'Bengal Leo sold listing', 'Successfully transferred to new owner.', 'Cat', 'Bengal', 15, 41000, 'Yekaterinburg', 'SOLD', '18', NOW() - INTERVAL '90 days', NOW(), NULL, NOW() - INTERVAL '7 days'),
    (32, 8, 'Old listing sold sample', 'Sold puppy from last season.', 'Dog', 'Maltese', 8, 28000, 'Sochi', 'SOLD', '25', NOW() - INTERVAL '120 days', NOW(), NULL, NOW() - INTERVAL '30 days');

INSERT INTO listing_flags (listing_id, reporter_id, reason, description, status, created_at)
VALUES
    (1, 9, 'SCAM', 'Price seems too low for this breed.', 'PENDING', NOW() - INTERVAL '2 days'),
    (2, 10, 'FAKE_INFO', 'Photos may not match the description.', 'PENDING', NOW() - INTERVAL '1 day'),
    (3, 11, 'ANIMAL_ABUSE', 'Concern about living conditions.', 'PENDING', NOW()),
    (4, 12, 'INAPPROPRIATE', 'Listing category may be wrong.', 'PENDING', NOW()),
    (5, 13, 'OTHER', 'Seller unresponsive to questions.', 'PENDING', NOW()),
    (7, 14, 'SCAM', 'Duplicate photos found online.', 'PENDING', NOW());

-- Synthesize a single status-history row for every non-DRAFT seed listing so
-- the "listing history" UI is not empty on the demo stand. We attribute the
-- change to the listing's seller with the SYSTEM role since real moderator
-- decisions never happened for these rows.
INSERT INTO listing_status_history (listing_id, from_status, to_status, changed_by_user_id, changed_by_role, comment, changed_at)
SELECT id,
       'DRAFT',
       status,
       seller_id,
       'SYSTEM',
       COALESCE(moderation_comment, 'Demo seed initial status'),
       COALESCE(sold_at, updated_at)
FROM listings
WHERE id BETWEEN 1 AND 99
  AND status <> 'DRAFT';

-- Sequences are bumped to at least 99 so anything created through the UI on
-- the demo profile lands at id >= 100, outside the BETWEEN 1 AND 99 window
-- this seed reclaims on every restart.
SELECT setval('listings_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM listings), 99));
SELECT setval('listing_flags_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM listing_flags), 99));
SELECT setval('listing_status_history_id_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM listing_status_history), 99));

DO $$
DECLARE
    listing_count INT;
    flag_count INT;
BEGIN
    SELECT COUNT(*) INTO listing_count FROM listings WHERE id BETWEEN 1 AND 99;
    IF listing_count < 30 THEN
        RAISE EXCEPTION 'Demo seed failed: expected at least 30 demo listings, got %', listing_count;
    END IF;
    SELECT COUNT(*) INTO flag_count FROM listing_flags WHERE listing_id BETWEEN 1 AND 99;
    IF flag_count < 5 THEN
        RAISE EXCEPTION 'Demo seed failed: expected at least 5 demo flags, got %', flag_count;
    END IF;
END $$;
