-- ========================================================
-- SCHÉMA DE BASE DE DONNÉES POSTGRESQL - TAK TAK QR ORDERING
-- ========================================================

-- Extension UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Table: CAFES
CREATE TABLE IF NOT EXISTS cafes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    logo_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table: TABLES (Les tables physiques du café)
CREATE TABLE IF NOT EXISTS tables (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    table_number INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_cafe_table UNIQUE (cafe_id, table_number)
);

-- 3. Table: CATEGORIES
CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Table: PRODUCTS
CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    category_id UUID NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 3) NOT NULL,
    is_available BOOLEAN DEFAULT TRUE,
    image_url TEXT,
    options_json JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Table: ORDERS
CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    table_id UUID,
    table_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    total_price NUMERIC(10, 3) NOT NULL,
    table_changed_alert BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Table: ORDER_ITEMS
CREATE TABLE IF NOT EXISTS order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id VARCHAR(100),
    product_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 3) NOT NULL,
    notes TEXT
);

-- 7. Table: WAITERS (Serveurs du café)
CREATE TABLE IF NOT EXISTS waiters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    pin_code VARCHAR(20) NOT NULL,
    shift_hours VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. Table: TABLE_ASSIGNMENTS (Zoning des tables par serveur)
CREATE TABLE IF NOT EXISTS table_assignments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    waiter_id UUID NOT NULL REFERENCES waiters(id) ON DELETE CASCADE,
    cafe_id UUID NOT NULL REFERENCES cafes(id) ON DELETE CASCADE,
    table_number INT NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes pour optimiser les performances
CREATE INDEX IF NOT EXISTS idx_cafes_slug ON cafes(slug);
CREATE INDEX IF NOT EXISTS idx_products_cafe_category ON products(cafe_id, category_id);
CREATE INDEX IF NOT EXISTS idx_orders_cafe_status ON orders(cafe_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_waiters_cafe_pin ON waiters(cafe_id, pin_code);
CREATE INDEX IF NOT EXISTS idx_table_assignments_waiter ON table_assignments(waiter_id);

CREATE TABLE IF NOT EXISTS floor_plans (
    id VARCHAR(36) PRIMARY KEY,
    cafe_id VARCHAR(36) NOT NULL,
    name VARCHAR(60) NOT NULL,
    width INTEGER NOT NULL DEFAULT 12,
    height INTEGER NOT NULL DEFAULT 8,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS floor_obstacles (
    id VARCHAR(36) PRIMARY KEY,
    floor_plan_id VARCHAR(36) NOT NULL,
    label VARCHAR(40) NOT NULL DEFAULT 'Mur',
    posx DOUBLE PRECISION NOT NULL DEFAULT 40,
    posy DOUBLE PRECISION NOT NULL DEFAULT 40,
    width DOUBLE PRECISION NOT NULL DEFAULT 30,
    height DOUBLE PRECISION NOT NULL DEFAULT 3
);

ALTER TABLE cafe_tables ADD COLUMN IF NOT EXISTS floor_plan_id VARCHAR(36);
CREATE INDEX IF NOT EXISTS idx_floor_plans_cafe ON floor_plans(cafe_id);
CREATE INDEX IF NOT EXISTS idx_floor_obstacles_plan ON floor_obstacles(floor_plan_id);
CREATE INDEX IF NOT EXISTS idx_cafe_tables_plan ON cafe_tables(floor_plan_id);

-- Harmonisation des anciens noms de statuts avec le workflow courant.
UPDATE orders SET status = 'RECEIVED' WHERE status = 'PENDING';
UPDATE orders SET status = 'READY' WHERE status = 'READY_FOR_PICKUP';
ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'RECEIVED';

-- ========================================================
-- DONNÉES DE DÉMO (SEED DATA)
-- ========================================================

-- Insert Café de démo
INSERT INTO cafes (id, name, slug, logo_url)
VALUES ('a1b2c3d4-e5f6-7890-abcd-111111111111', 'Monastir Lounge', 'monastir-lounge', 'https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80')
ON CONFLICT (slug) DO NOTHING;

-- Insert Waiters de démo
INSERT INTO waiters (id, cafe_id, name, pin_code, shift_hours) VALUES
('w0000000-0000-0000-0000-000000000001', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Youssef', '1234', '08:00 - 16:00 (Matin)'),
('w0000000-0000-0000-0000-000000000002', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Ahmed', '5678', '16:00 - 00:00 (Soir)'),
('w0000000-0000-0000-0000-000000000003', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Sirine', '9999', '12:00 - 20:00 (Continu)')
ON CONFLICT DO NOTHING;

-- Insert Tables (1 à 15)
DO $$
DECLARE
    i INT;
BEGIN
    FOR i IN 1..15 LOOP
        INSERT INTO tables (cafe_id, table_number)
        VALUES ('a1b2c3d4-e5f6-7890-abcd-111111111111', i)
        ON CONFLICT (cafe_id, table_number) DO NOTHING;
    END LOOP;
END $$;

-- Insert Catégories
INSERT INTO categories (id, cafe_id, name, sort_order) VALUES
('c0000000-0000-0000-0000-000000000001', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Boissons Chaudes', 1),
('c0000000-0000-0000-0000-000000000002', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Jus & Smoothies', 2),
('c0000000-0000-0000-0000-000000000003', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Desserts & Pâtisseries', 3),
('c0000000-0000-0000-0000-000000000004', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'Chicha / Shisha', 4)
ON CONFLICT DO NOTHING;

-- Insert Produits
INSERT INTO products (id, cafe_id, category_id, name, price, is_available, image_url, options_json) VALUES
('b0000000-0000-0000-0000-000000000001', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Café Express', 3.200, true, 'https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04?auto=format&fit=crop&w=400&q=80', '[{"name": "Sucre", "choices": ["Sans sucre", "Normal", "Très sucré"]}]'),
('b0000000-0000-0000-0000-000000000002', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Cappuccino Mousse Royale', 5.500, true, 'https://images.unsplash.com/photo-1572442388796-11668ba67e53?auto=format&fit=crop&w=400&q=80', '[{"name": "Lait", "choices": ["Lait entier", "Lait d''avoine (+1.000 TND)", "Lait d''amande (+1.500 TND)"]}, {"name": "Sucre", "choices": ["Sans sucre", "Moyen", "Sucré"]}]'),
('b0000000-0000-0000-0000-000000000003', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000001', 'Thé à la Menthe & Pignons', 4.800, true, 'https://images.unsplash.com/photo-1576092768241-dec231879fc3?auto=format&fit=crop&w=400&q=80', '[{"name": "Extra", "choices": ["Standard", "Extra Pignons (+2.000 TND)"]}]'),

('b0000000-0000-0000-0000-000000000004', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000002', 'Cocktail Monastir Sunset', 7.500, true, 'https://images.unsplash.com/photo-1536935338788-846bb9981813?auto=format&fit=crop&w=400&q=80', '[]'),
('b0000000-0000-0000-0000-000000000005', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000002', 'Jus d''Orange Frais Pressé', 4.500, true, 'https://images.unsplash.com/photo-1613478223719-2ab802602423?auto=format&fit=crop&w=400&q=80', '[]'),

('b0000000-0000-0000-0000-000000000006', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Cheesecake Spéculoos', 8.900, true, 'https://images.unsplash.com/photo-1533134242443-d4fd215305ad?auto=format&fit=crop&w=400&q=80', '[]'),
('b0000000-0000-0000-0000-000000000007', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000003', 'Fondant au Chocolat & Glace', 9.500, true, 'https://images.unsplash.com/photo-1606313564200-e75d5e30476c?auto=format&fit=crop&w=400&q=80', '[]'),

('b0000000-0000-0000-0000-000000000008', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000004', 'Chicha Pomme Menthe', 14.000, true, 'https://images.unsplash.com/photo-1527661591475-527312dd65f5?auto=format&fit=crop&w=400&q=80', '[{"name": "Tuyau", "choices": ["Standard", "Tuyau Glacé (+2.000 TND)"]}]'),
('b0000000-0000-0000-0000-000000000009', 'a1b2c3d4-e5f6-7890-abcd-111111111111', 'c0000000-0000-0000-0000-000000000004', 'Chicha Love 66', 16.000, true, 'https://images.unsplash.com/photo-1517256064527-09c73fc73e38?auto=format&fit=crop&w=400&q=80', '[]')
ON CONFLICT DO NOTHING;
