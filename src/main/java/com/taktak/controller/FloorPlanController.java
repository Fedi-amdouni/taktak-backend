package com.taktak.controller;

import com.taktak.model.CafeTable;
import com.taktak.model.FloorObstacle;
import com.taktak.model.FloorPlan;
import com.taktak.service.IFloorPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FloorPlanController {

    private final IFloorPlanService floorPlanService;

    @GetMapping("/cafes/{slug}/floor-plans")
    public ResponseEntity<List<FloorPlan>> getPlans(@PathVariable String slug) {
        return floorPlanService.getPlansBySlug(slug)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/cafes/{slug}/floor-plans")
    public ResponseEntity<FloorPlan> createPlan(@PathVariable String slug, @RequestBody FloorPlan input) {
        return floorPlanService.createPlan(slug, input)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/floor-plans/{id}")
    public ResponseEntity<FloorPlan> updatePlan(@PathVariable String id, @RequestBody FloorPlan input) {
        return floorPlanService.updatePlan(id, input)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/floor-plans/{id}")
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        if (!floorPlanService.deletePlan(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/floor-plans/{id}/obstacles")
    public ResponseEntity<List<FloorObstacle>> getObstacles(@PathVariable String id) {
        return floorPlanService.getObstacles(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/floor-plans/{id}/obstacles")
    public ResponseEntity<List<FloorObstacle>> saveObstacles(@PathVariable String id, @RequestBody List<FloorObstacle> input) {
        return floorPlanService.saveObstacles(id, input)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/floor-plans/{id}/tables")
    public ResponseEntity<List<CafeTable>> savePlanTables(@PathVariable String id, @RequestBody List<CafeTable> input) {
        return floorPlanService.savePlanTables(id, input)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
