package com.taktak.repository;

import com.taktak.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByCafeIdOrderBySortOrderAsc(UUID cafeId);
    Optional<Category> findByCafeIdAndNameIgnoreCase(UUID cafeId, String name);
}
