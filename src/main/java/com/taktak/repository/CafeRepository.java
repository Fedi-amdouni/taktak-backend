package com.taktak.repository;

import com.taktak.model.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CafeRepository extends JpaRepository<Cafe, UUID> {
    Optional<Cafe> findBySlug(String slug);
}
