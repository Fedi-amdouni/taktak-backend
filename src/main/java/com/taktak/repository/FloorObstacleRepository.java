package com.taktak.repository;

import com.taktak.model.FloorObstacle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FloorObstacleRepository extends JpaRepository<FloorObstacle, String> {
    List<FloorObstacle> findByFloorPlanId(String floorPlanId);
    void deleteByFloorPlanId(String floorPlanId);
}
