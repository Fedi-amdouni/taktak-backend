package com.taktak.service.impl;

import com.taktak.model.Category;
import com.taktak.repository.CategoryRepository;
import com.taktak.service.ICategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements ICategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public Category createCategory(Category payload) {
        if (payload.getCafeId() == null || payload.getName() == null || payload.getName().isBlank()) {
            throw new IllegalArgumentException("Informations de catégorie invalides");
        }

        UUID cafeId = payload.getCafeId();
        String name = payload.getName().trim();

        return categoryRepository.findByCafeIdAndNameIgnoreCase(cafeId, name)
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
                    return categoryRepository.save(category);
                });
    }
}
