-- Repeatable demo seed for auth-service (profile: demo)
-- Password for all demo users: demo1234

DELETE FROM sessions
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@demo.hvostid' OR id BETWEEN 1 AND 99);

DELETE FROM user_roles
WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@demo.hvostid' OR id BETWEEN 1 AND 99);

DELETE FROM users
WHERE email LIKE '%@demo.hvostid' OR id BETWEEN 1 AND 99;

INSERT INTO users (id, email, name, password_hash, phone, city, bio, rating, created_at)
VALUES
    (1, 'admin@demo.hvostid', 'Demo Admin', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', NULL, 'Moscow', 'Platform administrator', NULL, NOW()),
    (2, 'moderator@demo.hvostid', 'Demo Moderator', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', NULL, 'Moscow', 'Content moderator', NULL, NOW()),
    (3, 'seller1@demo.hvostid', 'Anna Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110001', 'Moscow', 'Responsible breeder, 5 years experience', 4.8, NOW()),
    (4, 'seller2@demo.hvostid', 'Ivan Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110002', 'Saint Petersburg', 'Cat lover and foster home', 4.5, NOW()),
    (5, 'seller3@demo.hvostid', 'Maria Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110003', 'Kazan', 'Small dog specialist', 4.9, NOW()),
    (6, 'seller4@demo.hvostid', 'Petr Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110004', 'Novosibirsk', 'Exotic birds and reptiles', 4.2, NOW()),
    (7, 'seller5@demo.hvostid', 'Olga Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110005', 'Yekaterinburg', 'Family-friendly puppies', 4.7, NOW()),
    (8, 'seller6@demo.hvostid', 'Dmitry Seller', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79001110006', 'Sochi', 'Coastal pet transfers', 4.4, NOW()),
    (9, 'buyer1@demo.hvostid', 'Elena Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220001', 'Moscow', 'Looking for a calm companion', NULL, NOW()),
    (10, 'buyer2@demo.hvostid', 'Sergey Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220002', 'Saint Petersburg', 'First-time pet owner', NULL, NOW()),
    (11, 'buyer3@demo.hvostid', 'Natalia Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220003', 'Kazan', 'Apartment with children', NULL, NOW()),
    (12, 'buyer4@demo.hvostid', 'Alexey Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220004', 'Novosibirsk', 'Active lifestyle, wants a dog', NULL, NOW()),
    (13, 'buyer5@demo.hvostid', 'Tatiana Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220005', 'Yekaterinburg', 'Allergy-conscious household', NULL, NOW()),
    (14, 'buyer6@demo.hvostid', 'Mikhail Buyer', '$2b$10$VrX314mASsJonseiWjmXTejQTwPaZWLXnl2fIo2qCC1IfdbvEP4hq', '+79002220006', 'Sochi', 'Experienced with cats', NULL, NOW());

INSERT INTO user_roles (user_id, role)
VALUES
    (1, 'ADMIN'),
    (2, 'MODERATOR'),
    (3, 'SELLER'),
    (4, 'SELLER'),
    (5, 'SELLER'),
    (6, 'SELLER'),
    (7, 'SELLER'),
    (8, 'SELLER'),
    (9, 'BUYER'),
    (10, 'BUYER'),
    (11, 'BUYER'),
    (12, 'BUYER'),
    (13, 'BUYER'),
    (14, 'BUYER');

SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));

DO $$
DECLARE
    user_count INT;
BEGIN
    SELECT COUNT(*) INTO user_count FROM users WHERE email LIKE '%@demo.hvostid';
    IF user_count < 10 THEN
        RAISE EXCEPTION 'Demo seed failed: expected at least 10 demo users, got %', user_count;
    END IF;
END $$;
