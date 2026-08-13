package com.taktak.service.impl;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.Category;
import com.taktak.model.Product;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.CategoryRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.service.ICafeService;
import com.taktak.service.IOrderService;
import lombok.RequiredArgsConstructor;
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
    @Transactional(readOnly = true)
    public List<CafeTable> getTablesByCafe(String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug).orElse(null);
        if (cafe == null) {
            return List.of();
        }
        return cafeTableRepository.findByCafeId(cafe.getId().toString());
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
}
