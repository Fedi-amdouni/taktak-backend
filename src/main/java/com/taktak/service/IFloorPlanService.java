package com.taktak.service;

import com.taktak.model.CafeTable;
import com.taktak.model.FloorObstacle;
import com.taktak.model.FloorPlan;

import java.util.List;
import java.util.Optional;

public interface IFloorPlanService {
    Optional<List<FloorPlan>> getPlansBySlug(String slug);
    Optional<FloorPlan> createPlan(String slug, FloorPlan input);
    Optional<FloorPlan> updatePlan(String id, FloorPlan input);
    boolean deletePlan(String id);
    Optional<List<FloorObstacle>> getObstacles(String id);
    Optional<List<FloorObstacle>> saveObstacles(String id, List<FloorObstacle> input);
    Optional<List<CafeTable>> savePlanTables(String id, List<CafeTable> input);
}
