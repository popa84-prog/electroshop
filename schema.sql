-- ============================================================
--  ElectroShop – test / demo data
--  Login credentials (plaintext -> BCrypt cost 12):
--    admin@electroshop.com    / admin123     (ROLE_ADMIN + ROLE_USER)
--    user@electroshop.com     / user123      (ROLE_USER)
--    customer@electroshop.com / password123  (ROLE_USER)
-- ============================================================

USE electroshop;

-- ------------------------------------------------------------
-- roles
-- ------------------------------------------------------------
INSERT INTO roles (id, name) VALUES
    (1, 'ROLE_USER'),
    (2, 'ROLE_ADMIN');

-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
INSERT INTO users (id, full_name, email, password, enabled) VALUES
    (1, 'Administrator', 'admin@electroshop.com',    '$2b$12$vfQq.5AxDZYTHpSf7y5lPunUec3VRyVWGJ9fs3C0A6pHdH8m9xD7m', 1),
    (2, 'Demo User',     'user@electroshop.com',     '$2b$12$bCN6nUcisuVbkG3fk8FYzeMc0vD.eJhgIPwJcrI1QYqOxxvwqQAbK', 1),
    (3, 'Ana Popescu',   'customer@electroshop.com', '$2b$12$3RYRUb6tA.eRZz1EfvfshO8Zyq683wHuLa8JRN9O/6X7wTPXQFHvS', 1);

-- ------------------------------------------------------------
-- user_roles
-- ------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id) VALUES
    (1, 1), (1, 2),   -- admin has USER + ADMIN
    (2, 1),           -- demo user
    (3, 1);           -- customer

-- ------------------------------------------------------------
-- products (10 electronics)
-- ------------------------------------------------------------
INSERT INTO products (id, name, description, price, stock_quantity, category, brand, image_url) VALUES
    (1,  'iPhone 15 Pro',            'Apple A17 Pro chip, 6.1" ProMotion display, titanium frame, 256GB.',        5499.00, 25, 'Smartphones', 'Apple',    'https://images.unsplash.com/photo-1592286927505-1def25115558?w=600'),
    (2,  'Samsung Galaxy S24 Ultra', '200MP camera, S-Pen, Snapdragon 8 Gen 3, 6.8" Dynamic AMOLED 2X.',          5999.00, 18, 'Smartphones', 'Samsung',  'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600'),
    (3,  'MacBook Air M3',           '13.6" Liquid Retina, Apple M3, 16GB RAM, 512GB SSD, up to 18h battery.',    7299.00, 12, 'Laptops',     'Apple',    'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600'),
    (4,  'Dell XPS 15',              'Intel Core i7-13700H, RTX 4050, 16GB RAM, 1TB SSD, 15.6" OLED 3.5K.',       8499.00,  9, 'Laptops',     'Dell',     'https://images.unsplash.com/photo-1593642702821-c8da6771f0c6?w=600'),
    (5,  'Sony WH-1000XM5',          'Industry-leading noise cancelling wireless over-ear headphones, 30h.',      1699.00, 40, 'Audio',       'Sony',     'https://images.unsplash.com/photo-1618366712010-f4ae9c647dcb?w=600'),
    (6,  'iPad Air 11 (M2)',         '11" Liquid Retina, Apple M2 chip, 128GB, Wi-Fi 6E, Touch ID.',              3299.00, 22, 'Tablets',     'Apple',    'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=600'),
    (7,  'Samsung 55" QLED 4K TV',   'Quantum HDR, 120Hz, Tizen OS, Gaming Hub, slim design.',                    4199.00, 15, 'TV',          'Samsung',  'https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?w=600'),
    (8,  'Logitech MX Master 3S',    'Ergonomic wireless mouse, 8K DPI, quiet clicks, USB-C, multi-device.',       549.00, 60, 'Accessories', 'Logitech', 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600'),
    (9,  'Asus ROG Strix G16',       'Intel Core i9, RTX 4070, 32GB DDR5, 1TB SSD, 16" 240Hz gaming laptop.',     9999.00,  7, 'Laptops',     'Asus',     'https://images.unsplash.com/photo-1603302576837-37561b2e2302?w=600'),
    (10, 'GoPro HERO12 Black',       '5.3K60 video, HDR, HyperSmooth 6.0, waterproof action camera.',             2499.00, 30, 'Cameras',     'GoPro',    'https://images.unsplash.com/photo-1526170375885-4d8ecf77b99f?w=600');

-- ------------------------------------------------------------
-- orders (5) — totals equal the sum of their order_items
-- ------------------------------------------------------------
INSERT INTO orders (id, user_id, status, total_amount, shipping_address, created_at) VALUES
    (1, 2, 'DELIVERED', 8897.00, 'Str. Victoriei 10, Bucuresti',      '2026-06-01 10:15:00'),
    (2, 3, 'SHIPPED',   8397.00, 'Bd. Unirii 55, Cluj-Napoca',        '2026-06-15 14:40:00'),
    (3, 2, 'PAID',      2499.00, 'Str. Victoriei 10, Bucuresti',      '2026-07-02 09:05:00'),
    (4, 3, 'PENDING',   9298.00, 'Bd. Unirii 55, Cluj-Napoca',        '2026-07-10 18:20:00'),
    (5, 1, 'DELIVERED', 4748.00, 'Str. Aviatorilor 3, Bucuresti',     '2026-07-14 11:00:00');

-- ------------------------------------------------------------
-- order_items — unit_price is a snapshot of the product price
-- ------------------------------------------------------------
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 1,  1, 5499.00),   -- iPhone 15 Pro
    (2, 1, 5,  2, 1699.00),   -- 2x Sony WH-1000XM5  => 5499 + 3398 = 8897.00
    (3, 2, 3,  1, 7299.00),   -- MacBook Air M3
    (4, 2, 8,  2,  549.00),   -- 2x MX Master 3S     => 7299 + 1098 = 8397.00
    (5, 3, 10, 1, 2499.00),   -- GoPro HERO12        => 2499.00
    (6, 4, 2,  1, 5999.00),   -- Galaxy S24 Ultra
    (7, 4, 6,  1, 3299.00),   -- iPad Air 11         => 5999 + 3299 = 9298.00
    (8, 5, 7,  1, 4199.00),   -- Samsung QLED TV
    (9, 5, 8,  1,  549.00);   -- MX Master 3S        => 4199 + 549 = 4748.00
