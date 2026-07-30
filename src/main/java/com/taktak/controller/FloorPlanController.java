package com.taktak.controller;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.FloorObstacle;
import com.taktak.model.FloorPlan;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.FloorObstacleRepository;
import com.taktak.repository.FloorPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FloorPlanController {
    private final CafeRepository cafeRepository;
    private final CafeTableRepository tableRepository;
    private final FloorPlanRepository planRepository;
    private final FloorObstacleRepository obstacleRepository;

    @GetMapping("/cafes/{slug}/floor-plans")
    public ResponseEntity<List<FloorPlan>> getPlans(@PathVariable String slug) {
        return cafeRepository.findBySlug(slug)
                .map(cafe -> ResponseEntity.ok(planRepository.findByCafeIdOrderBySortOrderAsc(cafe.getId().toString())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/cafes/{slug}/floor-plans")
    public ResponseEntity<FloorPlan> createPlan(@PathVariable String slug, @RequestBody FloorPlan input) {
        Optional<Cafe> cafe = cafeRepository.findBySlug(slug);
        if (cafe.isEmpty()) return ResponseEntity.notFound().build();
        FloorPlan plan = FloorPlan.builder()
                .cafeId(cafe.get().getId().toString())
                .name(cleanName(input.getName()))
                .width(clamp(input.getWidth(), 4, 50, 12))
                .height(clamp(input.getHeight(), 4, 50, 8))
                .sortOrder(planRepository.findByCafeIdOrderBySortOrderAsc(cafe.get().getId().toString()).size())
                .build();
        return ResponseEntity.ok(planRepository.save(plan));
    }

    @PutMapping("/floor-plans/{id}")
    public ResponseEntity<FloorPlan> updatePlan(@PathVariable String id, @RequestBody FloorPlan input) {
        return planRepository.findById(id).map(plan -> {
            plan.setName(cleanName(input.getName()));
            plan.setWidth(clamp(input.getWidth(), 4, 50, plan.getWidth()));
            plan.setHeight(clamp(input.getHeight(), 4, 50, plan.getHeight()));
            return ResponseEntity.ok(planRepository.save(plan));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/floor-plans/{id}")
    @Transactional
    public ResponseEntity<Void> deletePlan(@PathVariable String id) {
        if (!planRepository.existsById(id)) return ResponseEntity.notFound().build();
        obstacleRepository.deleteByFloorPlanId(id);
        tableRepository.deleteAll(tableRepository.findByFloorPlanId(id));
        planRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/floor-plans/{id}/obstacles")
    public ResponseEntity<List<FloorObstacle>> getObstacles(@PathVariable String id) {
        if (!planRepository.existsById(id)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(obstacleRepository.findByFloorPlanId(id));
    }

    @PutMapping("/floor-plans/{id}/obstacles")
    @Transactional
    public ResponseEntity<List<FloorObstacle>> saveObstacles(@PathVariable String id, @RequestBody List<FloorObstacle> input) {
        if (!planRepository.existsById(id)) return ResponseEntity.notFound().build();
        obstacleRepository.deleteByFloorPlanId(id);
        List<FloorObstacle> clean = input.stream().map(item -> FloorObstacle.builder()
                .floorPlanId(id)
                .label(cleanLabel(item.getLabel()))
                .posX(clampDouble(item.getPosX(), 0, 100, 40))
                .posY(clampDouble(item.getPosY(), 0, 100, 40))
                .width(clampDouble(item.getWidth(), 3, 100, 30))
                .height(clampDouble(item.getHeight(), 3, 100, 3))
                .build()).toList();
        return ResponseEntity.ok(obstacleRepository.saveAll(clean));
    }

    @PutMapping("/floor-plans/{id}/tables")
    @Transactional
    public ResponseEntity<List<CafeTable>> savePlanTables(@PathVariable String id, @RequestBody List<CafeTable> input) {
        Optional<FloorPlan> planOpt = planRepository.findById(id);
        if (planOpt.isEmpty()) return ResponseEntity.notFound().build();
        FloorPlan plan = planOpt.get();
        List<CafeTable> existing = tableRepository.findByFloorPlanId(id);
        Set<String> incomingIds = new HashSet<>();
        List<CafeTable> clean = new ArrayList<>();
        for (CafeTable table : input) {
            if (table.getId() != null && !table.getId().startsWith("temp_")) incomingIds.add(table.getId());
            else table.setId(null);
            table.setCafeId(plan.getCafeId());
            table.setFloorPlanId(id);
            table.setZoneName(plan.getName());
            table.setTableCode(normalizeCode(table.getTableCode(), table.getTableNumber()));
            clean.add(table);
        }
        tableRepository.deleteAll(existing.stream().filter(t -> !incomingIds.contains(t.getId())).toList());
        return ResponseEntity.ok(tableRepository.saveAll(clean));
    }

    private static String cleanName(String value) {
        String name = value == null ? "" : value.trim();
        return name.isEmpty() ? "Nouveau plan" : name.substring(0, Math.min(name.length(), 60));
    }

    private static String cleanLabel(String value) {
        String label = value == null ? "" : value.trim();
        return label.isEmpty() ? "Mur" : label.substring(0, Math.min(label.length(), 40));
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        return Math.max(min, Math.min(max, value == null ? fallback : value));
    }

    private static double clampDouble(Double value, double min, double max, double fallback) {
        return Math.max(min, Math.min(max, value == null ? fallback : value));
    }

    private static String normalizeCode(String value, Integer tableNumber) {
        String raw = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (raw.isEmpty()) raw = "T" + (tableNumber == null ? 0 : tableNumber);
        var matcher = java.util.regex.Pattern.compile("^([A-Z]+)[-_]?(\\d+)$").matcher(raw);
        if (!matcher.matches()) return raw;
        return matcher.group(1) + String.format("%02d", Integer.parseInt(matcher.group(2)));
    }
}
