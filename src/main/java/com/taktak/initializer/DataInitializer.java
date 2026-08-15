package com.taktak.initializer;

import com.taktak.model.*;
import com.taktak.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "TAKTAK_SEED_ENABLED", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CafeRepository cafeRepository;
    private final WaiterRepository waiterRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CafeTableRepository cafeTableRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final TableAssignmentRepository tableAssignmentRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Vérification et initialisation des données multi-cafés...");

        // 1. Cafe 1: Monastir Lounge
        Cafe monastir = initCafe("Monastir Lounge", "monastir-lounge", "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80");
        initTablesForCafe(monastir);
        initWaitersForCafe(monastir, List.of(
                Waiter.builder().cafeId(monastir.getId()).name("Youssef").pinCode("1234").shiftHours("08:00 - 16:00 (Shift Matin)").isActive(true).build(),
                Waiter.builder().cafeId(monastir.getId()).name("Ahmed").pinCode("5678").shiftHours("16:00 - 00:00 (Shift Soir)").isActive(true).build(),
                Waiter.builder().cafeId(monastir.getId()).name("Sirine").pinCode("9999").shiftHours("12:00 - 20:00 (Shift Continu)").isActive(true).build()
        ), Map.of("Youssef", List.of(1, 2, 3, 4, 5), "Ahmed", List.of(6, 7, 8, 9, 10), "Sirine", List.of(11, 12, 13, 14, 15)));
        initMenuForMonastir(monastir);
        migrateComboSlots(monastir);
        migrateLegacyTablesToPlans(monastir);

        // 2. Cafe 2: Carthage Premium Lounge
        Cafe carthage = initCafe("Carthage Premium Lounge", "carthage-lounge", "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=300&q=80");
        initTablesForCafe(carthage);
        initWaitersForCafe(carthage, List.of(
                Waiter.builder().cafeId(carthage.getId()).name("Karim").pinCode("1111").shiftHours("09:00 - 17:00").isActive(true).build(),
                Waiter.builder().cafeId(carthage.getId()).name("Amine").pinCode("2222").shiftHours("17:00 - 01:00").isActive(true).build()
        ), Map.of("Karim", List.of(1, 2, 3, 4, 5), "Amine", List.of(6, 7, 8, 9, 10)));
        initMenuForCarthage(carthage);
        migrateLegacyTablesToPlans(carthage);

        // 3. Cafe 3: Sousse Palm Beach Cafe
        Cafe sousse = initCafe("Sousse Palm Beach Cafe", "sousse-palm-beach", "https://images.unsplash.com/photo-1559925393-8be0ec4767c8?auto=format&fit=crop&w=300&q=80");
        initTablesForCafe(sousse);
        initWaitersForCafe(sousse, List.of(
                Waiter.builder().cafeId(sousse.getId()).name("Sami").pinCode("3333").shiftHours("10:00 - 18:00").isActive(true).build(),
                Waiter.builder().cafeId(sousse.getId()).name("Meriem").pinCode("4444").shiftHours("18:00 - 02:00").isActive(true).build()
        ), Map.of("Sami", List.of(1, 2, 3, 4, 5), "Meriem", List.of(6, 7, 8, 9, 10)));
        initMenuForSousse(sousse);
        migrateLegacyTablesToPlans(sousse);

        updateAllProductPrepTimes();

        log.info("Initialisation de la base de données multi-cafés terminée avec succès !");
    }

    private Cafe initCafe(String name, String slug, String logoUrl) {
        return cafeRepository.findBySlug(slug)
                .orElseGet(() -> {
                    log.info("Création du café : {} ({})", name, slug);
                    return cafeRepository.save(Cafe.builder()
                            .name(name)
                            .slug(slug)
                            .logoUrl(logoUrl)
                            .build());
                });
    }

    private void initWaitersForCafe(Cafe cafe, List<Waiter> defaultWaiters, Map<String, List<Integer>> initialAssignments) {
        List<Waiter> existing = waiterRepository.findByCafeIdAndIsActiveTrue(cafe.getId());
        if (existing.isEmpty()) {
            List<Waiter> savedWaiters = waiterRepository.saveAll(defaultWaiters);
            for (Waiter w : savedWaiters) {
                List<Integer> tables = initialAssignments.get(w.getName());
                if (tables != null && !tables.isEmpty()) {
                    List<TableAssignment> assignments = tables.stream()
                            .map(tn -> TableAssignment.builder()
                                    .waiterId(w.getId())
                                    .cafeId(cafe.getId())
                                    .tableNumber(tn)
                                    .build())
                            .toList();
                    tableAssignmentRepository.saveAll(assignments);
                }
            }
        }
    }

    private void initTablesForCafe(Cafe cafe) {
        if (cafeTableRepository.findByCafeId(cafe.getId().toString()).isEmpty()) {
            List<CafeTable> defaultTables = new ArrayList<>();

            // Zone 1: Terrasse (Tables 1 to 5, codes T1 to T5)
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 1, "T1", "Terrasse 🌿", 15.0, 20.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 2, "T2", "Terrasse 🌿", 35.0, 20.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 3, "T3", "Terrasse 🌿", 55.0, 20.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 4, "T4", "Terrasse 🌿", 75.0, 20.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 5, "T5", "Terrasse 🌿", 15.0, 50.0, 70.0, 70.0, "SQUARE", 4, 0));

            // Zone 2: Lounge Intérieur 1 (Tables 6 to 10, codes LI1 to LI5)
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 6, "LI1", "Lounge Intérieur 1 ☕", 20.0, 25.0, 70.0, 70.0, "SQUARE", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 7, "LI2", "Lounge Intérieur 1 ☕", 50.0, 25.0, 70.0, 70.0, "SQUARE", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 8, "LI3", "Lounge Intérieur 1 ☕", 80.0, 25.0, 70.0, 70.0, "SQUARE", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 9, "LI4", "Lounge Intérieur 1 ☕", 25.0, 60.0, 90.0, 60.0, "SOFA", 6, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 10, "LI5", "Lounge Intérieur 1 ☕", 65.0, 60.0, 90.0, 60.0, "SOFA", 6, 0));

            // Zone 3: Lounge Intérieur 2 (Tables 11 to 15, codes LII1 to LII5)
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 11, "LII1", "Lounge Intérieur 2 🛋️", 20.0, 25.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 12, "LII2", "Lounge Intérieur 2 🛋️", 50.0, 25.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 13, "LII3", "Lounge Intérieur 2 🛋️", 80.0, 25.0, 70.0, 70.0, "ROUND", 4, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 14, "LII4", "Lounge Intérieur 2 🛋️", 30.0, 60.0, 90.0, 60.0, "SOFA", 6, 0));
            defaultTables.add(new CafeTable(null, cafe.getId().toString(), 15, "LII5", "Lounge Intérieur 2 🛋️", 70.0, 60.0, 90.0, 60.0, "SOFA", 6, 0));

            cafeTableRepository.saveAll(defaultTables);
        }
    }

    private void migrateLegacyTablesToPlans(Cafe cafe) {
        String cafeId = cafe.getId().toString();
        if (!floorPlanRepository.findByCafeIdOrderBySortOrderAsc(cafeId).isEmpty()) return;

        List<CafeTable> legacyTables = cafeTableRepository.findByCafeId(cafeId).stream()
                .filter(table -> table.getFloorPlanId() == null)
                .toList();
        if (legacyTables.isEmpty()) return;

        Map<String, List<CafeTable>> byZone = new LinkedHashMap<>();
        for (CafeTable table : legacyTables) {
            String zone = table.getZoneName() == null || table.getZoneName().isBlank()
                    ? "Salle principale" : table.getZoneName().trim();
            byZone.computeIfAbsent(zone, ignored -> new ArrayList<>()).add(table);
        }

        int order = 0;
        for (Map.Entry<String, List<CafeTable>> entry : byZone.entrySet()) {
            FloorPlan plan = floorPlanRepository.save(FloorPlan.builder()
                    .cafeId(cafeId)
                    .name(entry.getKey())
                    .width(12)
                    .height(8)
                    .sortOrder(order++)
                    .build());
            for (CafeTable table : entry.getValue()) {
                table.setFloorPlanId(plan.getId());
                table.setTableCode(normalizeLegacyTableCode(table.getTableCode(), table.getTableNumber()));
            }
            cafeTableRepository.saveAll(entry.getValue());
        }
        log.info("Migration de {} anciennes tables vers {} plans pour {}", legacyTables.size(), byZone.size(), cafe.getSlug());
    }

    private String normalizeLegacyTableCode(String value, Integer tableNumber) {
        String raw = value == null || value.isBlank() ? "T" + tableNumber : value.trim().toUpperCase();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^([A-Z]+)[-_]?(\\d+)$").matcher(raw);
        return matcher.matches() ? matcher.group(1) + String.format("%02d", Integer.parseInt(matcher.group(2))) : raw;
    }

    private void initMenuForMonastir(Cafe cafe) {
        if (productRepository.findByCafeId(cafe.getId()).isEmpty()) {
            Category catBreakfast = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Petits Déjeuners & Formules").sortOrder(1).build());
            Category catHot = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Boissons Chaudes").sortOrder(2).build());
            Category catSweet = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Viennoiseries & Douceurs").sortOrder(3).build());
            Category catCold = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Boissons Fraîches").sortOrder(4).build());

            Product croissant = productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catSweet.getId())
                    .name("Croissant au Beurre")
                    .price(new BigDecimal("2.200"))
                    .isAvailable(true)
                    .badge("BEST_SELLER")
                    .imageUrl("https://images.unsplash.com/photo-1555507036-ab1f4038808a?auto=format&fit=crop&w=300&q=80")
                    .build());

            Product eau = productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catCold.getId())
                    .name("Bouteille d'eau Minérale 0.5L")
                    .price(new BigDecimal("1.500"))
                    .isAvailable(true)
                    .imageUrl("https://images.unsplash.com/photo-1548839140-29a749e1bc4e?auto=format&fit=crop&w=300&q=80")
                    .build());

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catHot.getId())
                    .name("Express Espresso")
                    .price(new BigDecimal("3.000"))
                    .promoPrice(new BigDecimal("2.500"))
                    .isAvailable(true)
                    .badge("PROMO")
                    .imageUrl("https://images.unsplash.com/photo-1510591509098-f4fdc6d0ff04?auto=format&fit=crop&w=300&q=80")
                    .suggestedProductIds(List.of(croissant.getId(), eau.getId()))
                    .build());

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catHot.getId())
                    .name("Cappuccino Mousse Amande")
                    .price(new BigDecimal("4.800"))
                    .isAvailable(true)
                    .badge("CHEF_SUGGESTION")
                    .imageUrl("https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=300&q=80")
                    .build());

            String comboSlots = "[{\"id\":\"slot_1\",\"title\":\"Choix de la Boisson Chaude\",\"selectionType\":\"CATEGORY\",\"categoryId\":\"" + catHot.getId() + "\",\"requiredQuantity\":1},{\"id\":\"slot_2\",\"title\":\"Choix de la Viennoiserie\",\"selectionType\":\"CATEGORY\",\"categoryId\":\"" + catSweet.getId() + "\",\"requiredQuantity\":1}]";

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catBreakfast.getId())
                    .name("Petit-Déjeuner Gourmand")
                    .description("1 Boisson chaude au choix (Espresso, Cappuccino...) + 1 Croissant au beurre + 1 Bouteille d'eau")
                    .price(new BigDecimal("7.500"))
                    .isAvailable(true)
                    .badge("BREAKFAST")
                    .isCombo(true)
                    .comboSlotsJson(comboSlots)
                    .imageUrl("https://images.unsplash.com/photo-1533089860892-a7c6f0a88666?auto=format&fit=crop&w=500&q=80")
                    .build());
        }
    }

    private void initMenuForCarthage(Cafe cafe) {
        if (productRepository.findByCafeId(cafe.getId()).isEmpty()) {
            Category catCold = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Smoothies & Jus").sortOrder(1).build());
            Category catPastry = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Pâtisseries Fine").sortOrder(2).build());

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catCold.getId())
                    .name("Café Glacé Caramel Salé")
                    .price(new BigDecimal("5.500"))
                    .promoPrice(new BigDecimal("4.500"))
                    .isAvailable(true)
                    .badge("CHEF_SUGGESTION")
                    .imageUrl("https://images.unsplash.com/photo-1517701604599-bb29b565090c?auto=format&fit=crop&w=300&q=80")
                    .build());

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catPastry.getId())
                    .name("Cheesecake aux Fruits Rouges")
                    .price(new BigDecimal("6.200"))
                    .isAvailable(true)
                    .badge("BEST_SELLER")
                    .imageUrl("https://images.unsplash.com/photo-1533134242443-d4fd215305ad?auto=format&fit=crop&w=300&q=80")
                    .build());
        }
    }

    private void initMenuForSousse(Cafe cafe) {
        if (productRepository.findByCafeId(cafe.getId()).isEmpty()) {
            Category catCocktail = categoryRepository.save(Category.builder().cafeId(cafe.getId()).name("Cocktails Palm Beach").sortOrder(1).build());

            productRepository.save(Product.builder()
                    .cafeId(cafe.getId())
                    .categoryId(catCocktail.getId())
                    .name("Mojito Frais Menthe Lime")
                    .price(new BigDecimal("7.000"))
                    .isAvailable(true)
                    .badge("BEST_SELLER")
                    .imageUrl("https://images.unsplash.com/photo-1551024709-8f23befc6f87?auto=format&fit=crop&w=300&q=80")
                    .build());
        }
    }

    private void migrateComboSlots(Cafe cafe) {
        List<Category> categories = categoryRepository.findByCafeIdOrderBySortOrderAsc(cafe.getId());
        if (categories.isEmpty()) return;

        Category catHot = categories.stream().filter(c -> c.getName().toLowerCase().contains("boisson") || c.getName().toLowerCase().contains("café")).findFirst().orElse(categories.get(0));
        Category catSweet = categories.stream().filter(c -> c.getName().toLowerCase().contains("gourmand") || c.getName().toLowerCase().contains("pâtisserie") || c.getName().toLowerCase().contains("viennois")).findFirst().orElse(categories.get(0));

        List<Product> products = productRepository.findByCafeId(cafe.getId());
        for (Product p : products) {
            if (p.getPrepTimeMinutes() == null || p.getPrepTimeMinutes() <= 0) {
                if (p.getBadge() != null && (p.getBadge().equals("BREAKFAST") || p.getBadge().equals("COMBO"))) {
                    p.setPrepTimeMinutes(12);
                } else if (p.getName().toLowerCase().contains("chicha")) {
                    p.setPrepTimeMinutes(15);
                } else if (p.getName().toLowerCase().contains("café") || p.getName().toLowerCase().contains("espresso") || p.getName().toLowerCase().contains("thé")) {
                    p.setPrepTimeMinutes(5);
                } else {
                    p.setPrepTimeMinutes(8);
                }
            }

            if ((p.getBadge() != null && (p.getBadge().equals("BREAKFAST") || p.getBadge().equals("COMBO"))) || (p.getName() != null && p.getName().toLowerCase().contains("petit"))) {
                if (p.getComboSlotsJson() == null || p.getComboSlotsJson().isBlank()) {
                    p.setIsCombo(true);
                    p.setDescription("1 Boisson chaude au choix (Café Express, Cappuccino...) + 1 Douceur / Pâtisserie au choix");
                    p.setComboSlotsJson("[{\"id\":\"slot_1\",\"title\":\"Choix du Café / Boisson Chaude\",\"selectionType\":\"CATEGORY\",\"categoryId\":\"" + catHot.getId() + "\",\"requiredQuantity\":1},{\"id\":\"slot_2\",\"title\":\"Choix de la Douceur / Pâtisserie\",\"selectionType\":\"CATEGORY\",\"categoryId\":\"" + catSweet.getId() + "\",\"requiredQuantity\":1}]");
                }
            }
            productRepository.save(p);
        }
    }

    private void updateAllProductPrepTimes() {
        List<Product> allProducts = productRepository.findAll();
        for (Product p : allProducts) {
            if (p.getPrepTimeMinutes() == null || p.getPrepTimeMinutes() <= 0) {
                String name = p.getName() != null ? p.getName().toLowerCase() : "";
                if (name.contains("chicha") || name.contains("mojito") || name.contains("cocktail")) {
                    p.setPrepTimeMinutes(15);
                } else if (name.contains("petit") || name.contains("formule") || "BREAKFAST".equals(p.getBadge()) || "COMBO".equals(p.getBadge())) {
                    p.setPrepTimeMinutes(12);
                } else if (name.contains("fondant") || name.contains("cheesecake") || name.contains("smoothie") || name.contains("jus") || name.contains("pâtisserie")) {
                    p.setPrepTimeMinutes(8);
                } else if (name.contains("cappuccino") || name.contains("latte") || name.contains("thé")) {
                    p.setPrepTimeMinutes(5);
                } else if (name.contains("express") || name.contains("espresso") || name.contains("eau") || name.contains("croissant")) {
                    p.setPrepTimeMinutes(3);
                } else {
                    p.setPrepTimeMinutes(6);
                }
                productRepository.save(p);
                log.info("Updated prep time for product: {} -> {} min", p.getName(), p.getPrepTimeMinutes());
            }
        }
    }

}
