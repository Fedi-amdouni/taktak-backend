package com.taktak.controller;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.Category;
import com.taktak.model.Product;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.CategoryRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/cafes")
@RequiredArgsConstructor
public class CafeController {

    private final CafeRepository cafeRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CafeTableRepository cafeTableRepository;
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<List<Cafe>> getAllCafes() {
        return ResponseEntity.ok(cafeRepository.findAll());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Cafe> getCafeBySlug(@PathVariable String slug) {
        return cafeRepository.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(
                        Cafe.builder()
                                .name("Monastir Lounge")
                                .slug(slug)
                                .logoUrl("https://images.unsplash.com/photo-1554118811-1e0d58224f24?auto=format&fit=crop&w=300&q=80")
                                .build()
                ));
    }

    @GetMapping("/{slug}/menu")
    public ResponseEntity<Map<String, Object>> getMenuBySlug(@PathVariable String slug) {
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

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{slug}/tables")
    public ResponseEntity<List<CafeTable>> getTablesByCafe(@PathVariable String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(cafeTableRepository.findByCafeId(cafe.getId().toString()));
    }

    @PostMapping("/{slug}/tables/batch")
    public ResponseEntity<List<CafeTable>> saveTablesBatch(@PathVariable String slug, @RequestBody List<CafeTable> tables) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) {
            return ResponseEntity.badRequest().build();
        }
        String cafeId = cafe.getId().toString();
        for (CafeTable t : tables) {
            t.setCafeId(cafeId);
        }
        List<CafeTable> saved = cafeTableRepository.saveAll(tables);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mimeType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            return ResponseEntity.ok(Map.of("imageUrl", dataUrl));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Échec de l'upload de l'image"));
        }
    }

    @GetMapping("/{slug}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable String slug) {
        Map<String, Object> analytics = orderService.getAnalyticsForCafe(slug);
        return ResponseEntity.ok(analytics);
    }
}
