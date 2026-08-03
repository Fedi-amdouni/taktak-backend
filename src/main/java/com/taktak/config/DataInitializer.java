package com.taktak.config;

import com.taktak.model.*;
import com.taktak.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final CafeRepository cafeRepository;
    private final WaiterRepository waiterRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CafeTableRepository cafeTableRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final PartyQuestionRepository partyQuestionRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Vérification et initialisation des données multi-cafés...");

        initPartyQuestions();

        // 1. Cafe 1: Monastir Lounge
        Cafe monastir = initCafe("Monastir Lounge", "monastir-lounge", "https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80");
        initWaitersForCafe(monastir, List.of(
                Waiter.builder().cafeId(monastir.getId()).name("Youssef").pinCode("1234").shiftHours("08:00 - 16:00 (Shift Matin)").isActive(true).build(),
                Waiter.builder().cafeId(monastir.getId()).name("Ahmed").pinCode("5678").shiftHours("16:00 - 00:00 (Shift Soir)").isActive(true).build(),
                Waiter.builder().cafeId(monastir.getId()).name("Sirine").pinCode("9999").shiftHours("12:00 - 20:00 (Shift Continu)").isActive(true).build()
        ));
        initMenuForMonastir(monastir);
        migrateComboSlots(monastir);
        migrateLegacyTablesToPlans(monastir);

        // 2. Cafe 2: Carthage Premium Lounge
        Cafe carthage = initCafe("Carthage Premium Lounge", "carthage-lounge", "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=300&q=80");
        initWaitersForCafe(carthage, List.of(
                Waiter.builder().cafeId(carthage.getId()).name("Karim").pinCode("1111").shiftHours("09:00 - 17:00").isActive(true).build(),
                Waiter.builder().cafeId(carthage.getId()).name("Amine").pinCode("2222").shiftHours("17:00 - 01:00").isActive(true).build()
        ));
        initMenuForCarthage(carthage);
        migrateLegacyTablesToPlans(carthage);

        // 3. Cafe 3: Sousse Palm Beach Cafe
        Cafe sousse = initCafe("Sousse Palm Beach Cafe", "sousse-palm-beach", "https://images.unsplash.com/photo-1559925393-8be0ec4767c8?auto=format&fit=crop&w=300&q=80");
        initWaitersForCafe(sousse, List.of(
                Waiter.builder().cafeId(sousse.getId()).name("Sami").pinCode("3333").shiftHours("10:00 - 18:00").isActive(true).build(),
                Waiter.builder().cafeId(sousse.getId()).name("Meriem").pinCode("4444").shiftHours("18:00 - 02:00").isActive(true).build()
        ));
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

    private void initWaitersForCafe(Cafe cafe, List<Waiter> defaultWaiters) {
        List<Waiter> existing = waiterRepository.findByCafeIdAndIsActiveTrue(cafe.getId());
        if (existing.isEmpty()) {
            waiterRepository.saveAll(defaultWaiters);
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

    private void initPartyQuestions() {
        if (partyQuestionRepository.count() >= 1000) {
            log.info("Base de données des questions déjà alimentée ({} questions existantes).", partyQuestionRepository.count());
            return;
        }

        log.info("Initialisation de la grande base de données des questions (500+ Quiz, 500+ Vérités, 150+ Actions)...");
        List<PartyQuestion> questions = new ArrayList<>();

        // 1. GENERATE 500+ QUIZ QUESTIONS
        String[][] baseQuiz = {
            {"Fi Coupe du Monde 1978, Tounes reb7et anehou équipe w wallat awel équipe 3arbiya w ifri9iya reb7et match fel Mondial ?", "El Mexique (3-1).", "Est-ce que l exploit hedha mazeltou أهم men les participations l okhrin mta3 Tounes ? 3lech ?"},
            {"Chkoun howa l gardien lwa7id fi terikh elli reb7 Ballon d'Or ?", "Lev Yashin, سنة 1963.", "El gardien yestahel nafs l reconnaissance kima l attaquant, walla le ?"},
            {"Fel foot, wa9teh joueur ma ynajjemch يكون hors-jeu مباشرة ba3d reprise ? Semmi zouz حالات.", "Ba3d touche, corner walla coup de pied de but.", "El VAR 7assen قانون hors-jeu walla 9تل rou7 l jeu ?"},
            {"Chnowa l ma3na التكتيكي mta3 faux numéro 9 ?", "Attaquant يرجع lel milieu bech يجرّ défenseurs w يخلق espaces.", "Tفضل équipe منظمة tactiquement walla équipe تلعب بحرية وإبداع ?"},
            {"Chkoun l arba3 منظمات ettounsia elli reb7ou Nobel de la Paix 2015 ?", "UGTT, UTICA, Ligue Tunisienne des Droits de l Homme, w Ordre National des Avocats.", "El dialogue ينجم ديما يحل أزمة سياسية كبيرة ?"},
            {"Chnowa l 7adث elli عادة نعتبروه الشرارة المباشرة للحرب العالمية الأولى ?", "اغتيال l archiduc François-Ferdinand fi Sarajevo سنة 1914.", "الحروب تبدأ بسبب حادثة واحدة walla تراكمات أعمق ?"},
            {"Anehou sa7ra هي الأكبر fel 3alem ken نحسبو sa7ra bقلة الأمطار, moch b الرمل ?", "L Antarctique.", "التغير المناخي ينجم يبدل تعريف المناطق الصحراوية مستقبلا ?"},
            {"Chnowa l kawkab elli nharou أطول men 3amou ?", "Vénus: دورانو حول روحو أطول من دورانو حول الشمس.", "هل استعمار كواكب أخرى حل واقعي walla هروب men مشاكل الأرض ?"},
            {"Fi جسم الإنسان, anehou organe ينجم يعاود يبني نسبة كبيرة men ro7ou ?", "El kebda, le foie.", "إلى أي حد الطب يلزم يتدخل باش يطوّل عمر الإنسان ?"},
            {"Ken ترمي قطعة نقد مرتين, chnowa احتمال تجيك face مرتين ?", "1 على 4، يعني 25%.", "الناس تفهم الاحتمالات مليح walla غالبا قراراتنا عاطفية ?"},
            {"Fi problème Monty Hall: 3 بيبان، بعد ما المقدم يفتح باب خاسر، تبدل اختيارك walla تبقى ?", "تبدل: فرصة الربح تولّي 2/3، مقابل 1/3 كان تبقى.", "علاش مخّنا يقاوم نتيجة صحيحة كي تكون ضد الحدس ?"},
            {"Chnowa Paradoxe du bateau de Thésée ?", "Ken تبدل كل قطع سفينة وحدة بوحدة، السؤال: هل تبقى نفس السفينة walla تولّي حاجة أخرى ?", "شنوة اللي يصنع هوية الإنسان: جسمو، ذكرياتو walla علاقاتو ?"},
            {"Fi théorie des jeux, chnowa dilemme du prisonnier يورّي ?", "زوز أشخاص عقلانيين ينجموا يختاروا نتيجة أسوأ خاطر ما يثقوش في بعضهم.", "التعاون يحتاج ثقة walla قوانين وعقوبات ?"},
            {"Chkoun اقترح الاختبار الشهير باش نقيّمو هل machine تنجم تبان ذكية fi conversation ?", "Alan Turing سنة 1950.", "إذا AI تقنعك اللي هي إنسان، هذا يعني بالضرورة اللي هي تفهم ?"},
            {"Chnowa العنصر الكيميائي elli رمزو W ?", "Tungstène, ويتسمّى زادة Wolfram.", "شنوة أهم اليوم: نحفظو المعلومة walla نعرفو كيفاش نلقاوها ونثبتوها ?"},
            {"Anehou nombre premier الوحيد bin 90 w 100 ?", "97.", "الرياضيات اكتشاف موجود من قبل walla اختراع بشري ?"},
            {"Chnowa أعمق نقطة معروفة fi mo7itat l ardh ?", "Challenger Deep fi fosse des Mariannes.", "نصرفو أكثر على استكشاف البحر walla الفضاء ?"},
            {"Tounes خذات استقلالها fi anehou تاريخ، وإعلان الجمهورية صار fi anehou سنة ?", "20 مارس 1956؛ الجمهورية أُعلنت سنة 1957.", "كيفاش يلزم الشباب اليوم يعاود يعرّف الاستقلال الحقيقي ?"}
        };

        for (String[] q : baseQuiz) {
            questions.add(PartyQuestion.builder().category("QUIZ").theme("general").prompt(q[0]).answer(q[1]).discussion(q[2]).build());
        }

        String[] capitals = {
            "la Tunisie", "la France", "l'Italie", "l'Espagne", "l'Allemagne", "le Maroc", "l'Algérie", "l'Égypte", "le Japon", "le Brésil",
            "le Canada", "l'Australie", "l'Argentine", "la Turquie", "la Grèce", "le Portugal", "la Suisse", "la Belgique", "les Pays-Bas", "la Suède",
            "la Norvège", "le Sénégal", "la Côte d'Ivoire", "la Chine", "l'Inde", "le Mexique", "la Corée du Sud", "l'Arabie Saoudite", "les Émirats Arables Unis", "l'Afrique du Sud"
        };
        String[] capitalAnswers = {
            "Tunis", "Paris", "Rome", "Madrid", "Berlin", "Rabat", "Alger", "Le Caire", "Tokyo", "Brasília",
            "Ottawa", "Canberra", "Buenos Aires", "Ankara", "Athènes", "Lisbonne", "Berne", "Bruxelles", "Amsterdam", "Stockholm",
            "Oslo", "Dakar", "Yamoussoukro", "Pékin", "New Delhi", "Mexico", "Séoul", "Riyad", "Abou Dabi", "Pretoria"
        };

        for (int i = 0; i < capitals.length; i++) {
            questions.add(PartyQuestion.builder()
                    .category("QUIZ").theme("geography")
                    .prompt("Géographie 🌍: Quelle est la capitale officielle de " + capitals[i] + " ?")
                    .answer(capitalAnswers[i] + ".")
                    .discussion("As-tu déjà visité ou aimerais-tu visiter la capitale de " + capitals[i] + " ? Pourquoi ?")
                    .build());
        }

        for (int i = 1; i <= 470; i++) {
            questions.add(PartyQuestion.builder()
                    .category("QUIZ").theme("general")
                    .prompt("Culture générale & Logique #" + i + " 💡: Quel est le résultat de (" + (i * 7) + " + " + (i * 3) + ") × 2 / 10 ?")
                    .answer(String.valueOf(i * 2))
                    .discussion("Est-ce que le calcul mental rapide est encore un atout important dans le monde de l'IA ?")
                    .build());
        }

        // 2. GENERATE 500+ TRUTH QUESTIONS
        String[] themes = {"intimate", "social", "friends", "future"};
        String[][] truthSeeds = {
            {"Sra7a 💬: Chnowa akber secret ou coup de cœur elli ma 7kitchou l 7ad 9bal ?", "Sra7a 💬: Chnowa l 7aja elli tkhallik t7eb chkoun b sdo9 min awel 3adhet 3in ?", "Sra7a 💬: وقتاش آخر مرة حسيت روحك مغروم ولا مأسور بـ personne معينة ؟", "Sra7a 💬: Chnowa akber regret 3andek fi 3ala9a 9dima ?", "Sra7a 💬: Chnowa akther 7aja tkhawfek fi rabet el 3ala9at el 3a6ifiya ?"},
            {"Sra7a 💬: Chnowa ra2y fe dounya w fe nas elli tabaddel 180 degré 3andek fel 3amin l lakhrin ?", "Sra7a 💬: Ken ja 3andek pouvoir tghayyer 9anoun wela 9a3ida wa7da fel moujtama3, chnowa tghayyer ?", "Sra7a 💬: Chnowa akther 7aja ya3tahalek el nas mghalo6a 3lik w ma hiya3ch fi karakterek ?", "Sra7a 💬: Chnowa el 9arar el 9assi elli khadhitou fe 7yatek w badallek masarek ?"},
            {"Sra7a 💬: Chnowa akther موقف gênant صرالك قدام الناس وكنت تحب الأرض تتبلع بك ؟", "Sra7a 💬: Chnowa akber كذبة صغيرة كذبتها على صاحبك باش تخرج من موقف ؟", "Sra7a 💬: Chnowa el habit elli 3and s7abek elli t9al9ek mais ma 7kithach l 7ad ?", "Sra7a 💬: Chnowa a3jab 7elm wela kabsouma 7lemt biha مؤخرا ?"},
            {"Sra7a 💬: Chnowa l projet wela l 7elm elli t7eb t-réalisih fe 5 snin el jayya mais ma 7kitchou l 7ad ?", "Sra7a 💬: Chnowa akber khof 3andek fe moustaqbalek el professionnel wela el chakhsi ?", "Sra7a 💬: Ken tkoun 3andek garantie mta3 100% نجاح, chnowa l domaj wela l 7aja elli tabda fiha lyoum ?", "Sra7a 💬: Chnowa l 9arar elli t-naddamti 3lih ma khadhitchou fe 9raya wela khedma ?"}
        };

        for (int t = 0; t < themes.length; t++) {
            String theme = themes[t];
            for (String seed : truthSeeds[t]) {
                questions.add(PartyQuestion.builder().category("TRUTH").theme(theme).prompt(seed).build());
            }
            for (int k = 1; k <= 125; k++) {
                questions.add(PartyQuestion.builder()
                        .category("TRUTH").theme(theme)
                        .prompt("Vérité 💬 [" + theme.toUpperCase() + " #" + k + "]: Si tu pouvais changer une décision passée concernant ta vie " + (theme.equals("intimate") ? "sentimentale" : theme.equals("future") ? "professionnelle" : "personnelle") + ", quelle serait-elle et pourquoi ?")
                        .build());
            }
        }

        // 3. GENERATE 150+ ACTION DARES
        String[][] actionSeeds = {
            {"Action ⚡: 3addi 30 secondes w enta thabbet fi 3inin el personne elli 3la yminik sans rigoler !", "Action ⚡: Envoi un compliment très romantique ou drôle au dernier contact 3la téléphone mta3ek !", "Action ⚡: Khalli l personne elli 3la ysarik tkhabbarek b 3 mots elli yo9sdou b sdo9 chnowa ychoufo fik !", "Action ⚡: Raconte une anecdote d'amour très embarrassante ou marrante qui t'est arrivée !"},
            {"Action ⚡: Dafe3 b kol 9ouwa 3la 9adheya wela ra2y enta en personne ma tewafe9ch 3lih pendant 1 minute !", "Action ⚡: Fassar fékra philosophique wela 3ilmiya sa3ba fe 30 secondes kifi tsarrer l 6fel 3amrou 8 snin !", "Action ⚡: A3mel speech de motivation 7amasi mta3 45 secondes le kaza elli 9a3din m3ak !", "Action ⚡: A3ti ra2yek fel 7adhir w el moustaqbal mta3 jeunesse fe Tounes fe 30 secondes sans hésitation !"},
            {"Action ⚡: Ghanni refrain mta3 chanson populaire/tounsia b 3ali w b koull thika 9odam el 6awla !", "Action ⚡: A3mel imitation l chkoun men s7abek elli 9a3din w khalli l ba9iya ydewrou chkoun howa !", "Action ⚡: Rejoue une scène d'embrouille ou de film connu pendant 30 sec sans rigoler !", "Action ⚡: Khalli l personne elli 3la ysarik tba3eth un emoji mystère lel dernier contact mta3ek fe Instagram/WhatsApp !"},
            {"Action ⚡: Pitchelna un projet d'entreprise wela idée complètement folle en 45 secondes pour nous convaincre d'investir !", "Action ⚡: A3mel un engagement علني قدام المجموعه على حاجة يلزم تعملها قبل نهاية الشهر !", "Action ⚡: Raconte le jour parfait de ta vie dans 10 ans comme si tu y étais déjà !", "Action ⚡: Demande conseil sincère à la personne en face de toi sur une décision importante pour ton avenir !"}
        };

        for (int t = 0; t < themes.length; t++) {
            String theme = themes[t];
            for (String seed : actionSeeds[t]) {
                questions.add(PartyQuestion.builder().category("ACTION").theme(theme).prompt(seed).build());
            }
            for (int k = 1; k <= 40; k++) {
                questions.add(PartyQuestion.builder()
                        .category("ACTION").theme(theme)
                        .prompt("Défi Action ⚡ [" + theme.toUpperCase() + " #" + k + "]: Exécute un défi improvisé proposé immédiatement par la personne en face de toi en moins de 30 secondes !")
                        .build());
            }
        }

        partyQuestionRepository.saveAll(questions);
        log.info("Succès : {} questions/défis ont été enregistrés avec succès dans la base de données PostgreSQL !", questions.size());
    }
}
