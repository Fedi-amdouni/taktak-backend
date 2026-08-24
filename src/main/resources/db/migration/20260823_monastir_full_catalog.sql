-- Catalogue complémentaire de Monastir Lounge.
-- Idempotent : met à jour les produits portant le même nom et insère les absents.
-- Les UUID sont déterministes et ne nécessitent aucune extension PostgreSQL.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM cafes WHERE slug = 'monastir-lounge') THEN
        RAISE EXCEPTION 'Cafe monastir-lounge introuvable';
    END IF;
END $$;

WITH category_seed(name, sort_order) AS (
    VALUES
        ('Petits Déjeuners & Formules', 1),
        ('Boissons Chaudes', 2),
        ('Viennoiseries & Douceurs', 3),
        ('Boissons Fraîches', 4),
        ('Snacks Salés', 5)
)
INSERT INTO categories (id, cafe_id, name, sort_order)
SELECT
    md5('category:monastir-lounge:' || seed.name)::uuid,
    cafe.id,
    seed.name,
    seed.sort_order
FROM category_seed seed
CROSS JOIN cafes cafe
WHERE cafe.slug = 'monastir-lounge'
  AND NOT EXISTS (
      SELECT 1
      FROM categories existing
      WHERE existing.cafe_id = cafe.id
        AND lower(existing.name) = lower(seed.name)
  );

WITH catalog(category_name, name, price, image_url, prep_time_minutes, badge, description) AS (
    VALUES
        ('Petits Déjeuners & Formules', 'Formule Brunch Tunisien', 12.900, 'https://images.unsplash.com/photo-1565252556328-92ee4a9a0983?auto=format&fit=crop&w=900&q=80', 12, 'BREAKFAST', 'Café ou thé, œufs, pain artisanal et douceur du jour.'),
        ('Petits Déjeuners & Formules', 'Toast Avocat & Œuf', 10.500, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 10, 'CHEF_SUGGESTION', 'Pain grillé, avocat citronné, œuf coulant et jeunes pousses.'),
        ('Petits Déjeuners & Formules', 'Pancakes Miel & Fruits', 9.500, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 10, 'NEW', 'Pancakes moelleux, miel et fruits de saison.'),
        ('Petits Déjeuners & Formules', 'Œufs Brouillés & Toast', 8.900, 'https://images.unsplash.com/photo-1565252556328-92ee4a9a0983?auto=format&fit=crop&w=900&q=80', 8, NULL, 'Œufs crémeux, toast beurré et salade fraîche.'),

        ('Boissons Chaudes', 'Double Espresso', 3.700, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 3, 'BEST_SELLER', 'Double shot intense, servi court.'),
        ('Boissons Chaudes', 'Café Crème', 4.200, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 4, NULL, 'Espresso allongé d''une touche de crème.'),
        ('Boissons Chaudes', 'Latte Vanille', 5.500, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 5, 'CHEF_SUGGESTION', 'Lait velouté, espresso et vanille douce.'),
        ('Boissons Chaudes', 'Thé à la Menthe', 3.500, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Thé vert parfumé à la menthe fraîche.'),
        ('Boissons Chaudes', 'Chocolat Chaud Maison', 5.200, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 6, 'NEW', 'Chocolat onctueux, cacao intense et lait chaud.'),

        ('Viennoiseries & Douceurs', 'Pain au Chocolat', 2.500, 'https://images.unsplash.com/photo-1647544301437-36acef1eff9d?auto=format&fit=crop&w=900&q=80', 3, 'BEST_SELLER', 'Viennoiserie pur beurre au chocolat fondant.'),
        ('Viennoiseries & Douceurs', 'Cookie Trois Chocolats', 3.800, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 3, NULL, 'Cookie croustillant, chocolat noir, lait et blanc.'),
        ('Viennoiseries & Douceurs', 'Cheesecake Fruits Rouges', 6.900, 'https://images.unsplash.com/photo-1529942458412-eda69f76291d?auto=format&fit=crop&w=900&q=80', 5, 'CHEF_SUGGESTION', 'Cheesecake crémeux, coulis de fruits rouges.'),
        ('Viennoiseries & Douceurs', 'Fondant Chocolat', 6.500, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 7, 'BEST_SELLER', 'Cœur coulant au chocolat noir.'),
        ('Viennoiseries & Douceurs', 'Tiramisu Maison', 7.200, 'https://images.unsplash.com/photo-1529942458412-eda69f76291d?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Crème mascarpone, café et cacao.'),

        ('Boissons Fraîches', 'Citronnade Menthe', 5.000, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 4, 'BEST_SELLER', 'Citron frais, menthe et glace pilée.'),
        ('Boissons Fraîches', 'Smoothie Mangue Passion', 7.500, 'https://images.unsplash.com/photo-1747232725118-bd9f8dc1ff49?auto=format&fit=crop&w=900&q=80', 6, 'CHEF_SUGGESTION', 'Mangue, passion et banane mixées minute.'),
        ('Boissons Fraîches', 'Iced Latte Caramel', 6.500, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 5, 'NEW', 'Espresso, lait frais, caramel et glaçons.'),
        ('Boissons Fraîches', 'Jus d''Orange Pressé', 6.000, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Oranges pressées à la demande.'),
        ('Boissons Fraîches', 'Thé Glacé Pêche', 5.500, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 4, NULL, 'Thé noir, pêche et citron frais.'),
        ('Boissons Fraîches', 'Frappé Chocolat', 7.000, 'https://images.unsplash.com/photo-1747232725118-bd9f8dc1ff49?auto=format&fit=crop&w=900&q=80', 6, 'BEST_SELLER', 'Boisson glacée au chocolat et crème légère.'),

        ('Snacks Salés', 'Club Sandwich Poulet', 12.500, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 10, 'BEST_SELLER', 'Poulet mariné, œuf, salade, tomate et frites.'),
        ('Snacks Salés', 'Panini Thon Fromage', 10.900, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 9, NULL, 'Thon, fromage fondant, tomate et herbes.'),
        ('Snacks Salés', 'Toast Mozzarella Pesto', 10.500, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 8, 'NEW', 'Mozzarella fondante, pesto basilic et tomate.'),
        ('Snacks Salés', 'Salade César', 13.500, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 9, 'CHEF_SUGGESTION', 'Poulet grillé, parmesan, croûtons et sauce César.'),
        ('Snacks Salés', 'Frites Maison', 5.000, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 7, NULL, 'Pommes de terre fraîches, sel marin et sauce au choix.')
), resolved AS (
    SELECT
        cafe.id AS cafe_id,
        (SELECT category.id
         FROM categories category
         WHERE category.cafe_id = cafe.id
           AND lower(category.name) = lower(catalog.category_name)
         ORDER BY category.id
         LIMIT 1) AS category_id,
        catalog.*
    FROM catalog
    CROSS JOIN cafes cafe
    WHERE cafe.slug = 'monastir-lounge'
)
UPDATE products product
SET category_id = resolved.category_id,
    price = resolved.price,
    image_url = resolved.image_url,
    prep_time_minutes = resolved.prep_time_minutes,
    badge = resolved.badge,
    description = resolved.description
FROM resolved
WHERE product.cafe_id = resolved.cafe_id
  AND lower(product.name) = lower(resolved.name);

WITH catalog(category_name, name, price, image_url, prep_time_minutes, badge, description) AS (
    VALUES
        ('Petits Déjeuners & Formules', 'Formule Brunch Tunisien', 12.900, 'https://images.unsplash.com/photo-1565252556328-92ee4a9a0983?auto=format&fit=crop&w=900&q=80', 12, 'BREAKFAST', 'Café ou thé, œufs, pain artisanal et douceur du jour.'),
        ('Petits Déjeuners & Formules', 'Toast Avocat & Œuf', 10.500, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 10, 'CHEF_SUGGESTION', 'Pain grillé, avocat citronné, œuf coulant et jeunes pousses.'),
        ('Petits Déjeuners & Formules', 'Pancakes Miel & Fruits', 9.500, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 10, 'NEW', 'Pancakes moelleux, miel et fruits de saison.'),
        ('Petits Déjeuners & Formules', 'Œufs Brouillés & Toast', 8.900, 'https://images.unsplash.com/photo-1565252556328-92ee4a9a0983?auto=format&fit=crop&w=900&q=80', 8, NULL, 'Œufs crémeux, toast beurré et salade fraîche.'),
        ('Boissons Chaudes', 'Double Espresso', 3.700, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 3, 'BEST_SELLER', 'Double shot intense, servi court.'),
        ('Boissons Chaudes', 'Café Crème', 4.200, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 4, NULL, 'Espresso allongé d''une touche de crème.'),
        ('Boissons Chaudes', 'Latte Vanille', 5.500, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 5, 'CHEF_SUGGESTION', 'Lait velouté, espresso et vanille douce.'),
        ('Boissons Chaudes', 'Thé à la Menthe', 3.500, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Thé vert parfumé à la menthe fraîche.'),
        ('Boissons Chaudes', 'Chocolat Chaud Maison', 5.200, 'https://images.unsplash.com/photo-1564327367919-cb377ea6a88f?auto=format&fit=crop&w=900&q=80', 6, 'NEW', 'Chocolat onctueux, cacao intense et lait chaud.'),
        ('Viennoiseries & Douceurs', 'Pain au Chocolat', 2.500, 'https://images.unsplash.com/photo-1647544301437-36acef1eff9d?auto=format&fit=crop&w=900&q=80', 3, 'BEST_SELLER', 'Viennoiserie pur beurre au chocolat fondant.'),
        ('Viennoiseries & Douceurs', 'Cookie Trois Chocolats', 3.800, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 3, NULL, 'Cookie croustillant, chocolat noir, lait et blanc.'),
        ('Viennoiseries & Douceurs', 'Cheesecake Fruits Rouges', 6.900, 'https://images.unsplash.com/photo-1529942458412-eda69f76291d?auto=format&fit=crop&w=900&q=80', 5, 'CHEF_SUGGESTION', 'Cheesecake crémeux, coulis de fruits rouges.'),
        ('Viennoiseries & Douceurs', 'Fondant Chocolat', 6.500, 'https://images.unsplash.com/photo-1707126186318-a3dde00d600e?auto=format&fit=crop&w=900&q=80', 7, 'BEST_SELLER', 'Cœur coulant au chocolat noir.'),
        ('Viennoiseries & Douceurs', 'Tiramisu Maison', 7.200, 'https://images.unsplash.com/photo-1529942458412-eda69f76291d?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Crème mascarpone, café et cacao.'),
        ('Boissons Fraîches', 'Citronnade Menthe', 5.000, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 4, 'BEST_SELLER', 'Citron frais, menthe et glace pilée.'),
        ('Boissons Fraîches', 'Smoothie Mangue Passion', 7.500, 'https://images.unsplash.com/photo-1747232725118-bd9f8dc1ff49?auto=format&fit=crop&w=900&q=80', 6, 'CHEF_SUGGESTION', 'Mangue, passion et banane mixées minute.'),
        ('Boissons Fraîches', 'Iced Latte Caramel', 6.500, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 5, 'NEW', 'Espresso, lait frais, caramel et glaçons.'),
        ('Boissons Fraîches', 'Jus d''Orange Pressé', 6.000, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 5, NULL, 'Oranges pressées à la demande.'),
        ('Boissons Fraîches', 'Thé Glacé Pêche', 5.500, 'https://images.unsplash.com/photo-1664888272806-f96168766335?auto=format&fit=crop&w=900&q=80', 4, NULL, 'Thé noir, pêche et citron frais.'),
        ('Boissons Fraîches', 'Frappé Chocolat', 7.000, 'https://images.unsplash.com/photo-1747232725118-bd9f8dc1ff49?auto=format&fit=crop&w=900&q=80', 6, 'BEST_SELLER', 'Boisson glacée au chocolat et crème légère.'),
        ('Snacks Salés', 'Club Sandwich Poulet', 12.500, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 10, 'BEST_SELLER', 'Poulet mariné, œuf, salade, tomate et frites.'),
        ('Snacks Salés', 'Panini Thon Fromage', 10.900, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 9, NULL, 'Thon, fromage fondant, tomate et herbes.'),
        ('Snacks Salés', 'Toast Mozzarella Pesto', 10.500, 'https://images.unsplash.com/photo-1709689156424-16fe0e05b47b?auto=format&fit=crop&w=900&q=80', 8, 'NEW', 'Mozzarella fondante, pesto basilic et tomate.'),
        ('Snacks Salés', 'Salade César', 13.500, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 9, 'CHEF_SUGGESTION', 'Poulet grillé, parmesan, croûtons et sauce César.'),
        ('Snacks Salés', 'Frites Maison', 5.000, 'https://images.unsplash.com/photo-1583527825770-8bd0bfb1f1c1?auto=format&fit=crop&w=900&q=80', 7, NULL, 'Pommes de terre fraîches, sel marin et sauce au choix.')
), resolved AS (
    SELECT
        cafe.id AS cafe_id,
        (SELECT category.id
         FROM categories category
         WHERE category.cafe_id = cafe.id
           AND lower(category.name) = lower(catalog.category_name)
         ORDER BY category.id
         LIMIT 1) AS category_id,
        catalog.*
    FROM catalog
    CROSS JOIN cafes cafe
    WHERE cafe.slug = 'monastir-lounge'
)
INSERT INTO products (
    id, cafe_id, category_id, name, price, is_available, image_url,
    description, is_combo, prep_time_minutes, badge
)
SELECT
    md5('product:monastir-lounge:' || resolved.name)::uuid,
    resolved.cafe_id,
    resolved.category_id,
    resolved.name,
    resolved.price,
    true,
    resolved.image_url,
    resolved.description,
    false,
    resolved.prep_time_minutes,
    resolved.badge
FROM resolved
WHERE resolved.category_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM products existing
      WHERE existing.cafe_id = resolved.cafe_id
        AND lower(existing.name) = lower(resolved.name)
  );

COMMIT;

-- Vérification attendue après application : 30 produits pour le catalogue actuel.
SELECT category.name AS category, count(product.id) AS products
FROM categories category
LEFT JOIN products product ON product.category_id = category.id
JOIN cafes cafe ON cafe.id = category.cafe_id
WHERE cafe.slug = 'monastir-lounge'
GROUP BY category.name
ORDER BY category.name;
