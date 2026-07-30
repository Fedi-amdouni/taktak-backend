package com.taktak.repository;

import com.taktak.model.CafeTable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CafeTableRepository extends JpaRepository<CafeTable, String> {
    List<CafeTable> findByCafeId(String cafeId);
    Optional<CafeTable> findByCafeIdAndTableNumber(String cafeId, Integer tableNumber);
    List<CafeTable> findByFloorPlanId(String floorPlanId);
    void deleteByCafeId(String cafeId);
}
