package com.taktak.service.impl;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.Category;
import com.taktak.model.Product;
import com.taktak.model.Order;
import com.taktak.model.OrderStatus;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.CategoryRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.service.ICafeService;
import com.taktak.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CafeServiceImpl implements ICafeService {

    private final CafeRepository cafeRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CafeTableRepository cafeTableRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final IOrderService orderService;

    @Override
    @Transactional(readOnly = true)
    public List<Cafe> getAllCafes() {
        return cafeRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Cafe getCafeBySlug(String slug) {
        return cafeRepository.findBySlug(slug)
                .orElseGet(() -> Cafe.builder()
                        .name("Monastir Lounge")
                        .slug(slug)
                        .logoUrl("https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80")
                        .build()
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getMenuBySlug(String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        Map<String, Object> response = new HashMap<>();

        if (cafe != null) {
            List<Category> categories = categoryRepository.findByCafeIdOrderBySortOrderAsc(cafe.getId());
            List<Product> products = productRepository.findByCafeId(cafe.getId());

            Map<UUID, Product> productMap = products.stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

            List<Map<String, Object>> enrichedProducts = products.stream().map(p -> {
                Map<String, Object> pMap = new HashMap<>();
                pMap.put("id", p.getId());
                pMap.put("cafeId", p.getCafeId());
                pMap.put("categoryId", p.getCategoryId());
                pMap.put("name", p.getName());
                pMap.put("price", p.getPrice());
                pMap.put("promoPrice", p.getPromoPrice());
                pMap.put("isAvailable", p.getIsAvailable());
                pMap.put("imageUrl", p.getImageUrl());
                pMap.put("optionsJson", p.getOptionsJson());
                pMap.put("description", p.getDescription());
                pMap.put("isCombo", p.getIsCombo());
                pMap.put("comboSlotsJson", p.getComboSlotsJson());
                pMap.put("prepTimeMinutes", p.getPrepTimeMinutes());
                pMap.put("badge", p.getBadge());

                List<Map<String, Object>> suggestions = new ArrayList<>();
                if (p.getSuggestedProductIds() != null) {
                    for (UUID sugId : p.getSuggestedProductIds()) {
                        Product sug = productMap.get(sugId);
                        if (sug != null && Boolean.TRUE.equals(sug.getIsAvailable())) {
                            Map<String, Object> sData = new HashMap<>();
                            sData.put("id", sug.getId());
                            sData.put("name", sug.getName());
                            sData.put("price", sug.getPromoPrice() != null ? sug.getPromoPrice() : sug.getPrice());
                            sData.put("imageUrl", sug.getImageUrl());
                            sData.put("badge", sug.getBadge());
                            suggestions.add(sData);
                        }
                    }
                }
                pMap.put("suggestedProducts", suggestions);
                return pMap;
            }).collect(Collectors.toList());

            response.put("categories", categories);
            response.put("products", enrichedProducts);
        } else {
            response.put("categories", List.of());
            response.put("products", List.of());
        }

        return response;
    }

    @Override
    @Transactional
    public List<CafeTable> getTablesByCafe(String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) {
            return List.of();
        }
        List<CafeTable> tables = cafeTableRepository.findByCafeId(cafe.getId().toString());
        boolean modified = false;
        for (CafeTable table : tables) {
            if (table.getSessionToken() == null || table.getSessionToken().isBlank()) {
                table.setSessionToken(java.util.UUID.randomUUID().toString());
                modified = true;
            }
        }
        if (modified) {
            tables = cafeTableRepository.saveAll(tables);
        }
        return tables;
    }

    @Override
    @Transactional
    public List<CafeTable> saveTablesBatch(String slug, List<CafeTable> tables) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) {
            throw new IllegalArgumentException("Café non trouvé");
        }
        String cafeId = cafe.getId().toString();
        for (CafeTable t : tables) {
            t.setCafeId(cafeId);
        }
        return cafeTableRepository.saveAll(tables);
    }

    @Override
    public Map<String, String> uploadImage(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            return Map.of("imageUrl", dataUrl);
        } catch (IOException e) {
            throw new RuntimeException("Échec de l'upload de l'image", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalytics(String slug) {
        return orderService.getAnalyticsForCafe(slug);
    }

    @Override
    @Transactional
    public Cafe updateLocationSettings(String slug, Double latitude, Double longitude, Double geofenceRadiusMeters) {
        Cafe cafe = cafeRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Café introuvable : " + slug));

        if (latitude != null) cafe.setLatitude(latitude);
        if (longitude != null) cafe.setLongitude(longitude);
        if (geofenceRadiusMeters != null) cafe.setGeofenceRadiusMeters(geofenceRadiusMeters);

        return cafeRepository.save(cafe);
    }

    @Override
    @Transactional
    public CafeTable toggleTableGames(String slug, Integer tableNumber, Boolean enabled) {
        Cafe cafe = cafeRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Café introuvable : " + slug));

        CafeTable table = cafeTableRepository.findByCafeIdAndTableNumber(cafe.getId().toString(), tableNumber)
                .orElseGet(() -> {
                    CafeTable newT = new CafeTable();
                    newT.setCafeId(cafe.getId().toString());
                    newT.setTableNumber(tableNumber);
                    return newT;
                });

        table.setGamesEnabledOverride(enabled);
        CafeTable saved = cafeTableRepository.save(table);

        // Diffuser mise à jour temps réel à la table
        messagingTemplate.convertAndSend("/topic/tables/" + slug + "/" + tableNumber, getTableStatus(slug, tableNumber, null));

        return saved;
    }

    @Override
    @Transactional
    public Map<String, Object> getTableStatus(String slug, Integer tableNumber, String providedSessionToken) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) return Map.of(
                "tableNumber", tableNumber,
                "hasActiveOrders", false,
                "gamesAllowed", false,
                "sessionValid", false,
                "gamesEnabledOverride", "AUTO"
        );

        // 1. Vérifier si la table a des commandes en cours (non payées / non archivées)
        List<Order> orders = orderRepository.findByCafeIdOrderByCreatedAtDesc(cafe.getId());
        boolean hasActiveOrders = orders.stream().anyMatch(o ->
                o.getTableNumber() != null && o.getTableNumber().equals(tableNumber)
                && o.getStatus() != OrderStatus.PAID
                && o.getStatus() != OrderStatus.ARCHIVED
                && o.getStatus() != OrderStatus.CANCELLED
        );

        // 2. Vérifier override spécifique gérant/staff
        Boolean override = null;
        String currentSessionToken = null;
        Optional<CafeTable> tableOpt = cafeTableRepository.findByCafeIdAndTableNumber(cafe.getId().toString(), tableNumber);
        if (tableOpt.isPresent()) {
            CafeTable table = tableOpt.get();
            override = table.getGamesEnabledOverride();
            if (table.getSessionToken() == null || table.getSessionToken().isBlank()) {
                table.setSessionToken(java.util.UUID.randomUUID().toString());
                cafeTableRepository.save(table);
            }
            currentSessionToken = table.getSessionToken();
        }

        // Si override fixé par staff (true ou false), sinon automatique selon commande active
        boolean gamesAllowed = override != null ? override : hasActiveOrders;
        boolean sessionValid = sessionTokenMatches(currentSessionToken, providedSessionToken);

        return Map.of(
                "tableNumber", tableNumber,
                "hasActiveOrders", hasActiveOrders,
                "gamesAllowed", gamesAllowed,
                "sessionValid", sessionValid,
                "gamesEnabledOverride", override != null ? override : "AUTO"
        );
    }

    private boolean sessionTokenMatches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}
