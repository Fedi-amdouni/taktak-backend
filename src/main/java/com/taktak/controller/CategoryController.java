package com.taktak.controller;

import com.taktak.model.Category;
import com.taktak.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryRepository categoryRepository;

    @PostMapping
    public ResponseEntity<Category> createCategory(@RequestBody Category payload) {
        if (payload.getCafeId() == null || payload.getName() == null || payload.getName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UUID cafeId = payload.getCafeId();
        String name = payload.getName().trim();

        return categoryRepository.findByCafeIdAndNameIgnoreCase(cafeId, name)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    int nextSortOrder = categoryRepository.findByCafeIdOrderBySortOrderAsc(cafeId).stream()
                            .map(Category::getSortOrder)
                            .filter(order -> order != null)
                            .max(Comparator.naturalOrder())
                            .orElse(0) + 1;

                    Category category = Category.builder()
                            .cafeId(cafeId)
                            .name(name)
                            .sortOrder(nextSortOrder)
                            .build();
                    return ResponseEntity.ok(categoryRepository.save(category));
                });
    }
}
