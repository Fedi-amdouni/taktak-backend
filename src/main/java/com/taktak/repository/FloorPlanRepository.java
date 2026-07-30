package com.taktak.repository;

import com.taktak.model.FloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FloorPlanRepository extends JpaRepository<FloorPlan, String> {
    List<FloorPlan> findByCafeIdOrderBySortOrderAsc(String cafeId);
}
