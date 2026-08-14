package com.taktak.service.impl;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.FloorObstacle;
import com.taktak.model.FloorPlan;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.FloorObstacleRepository;
import com.taktak.repository.FloorPlanRepository;
import com.taktak.service.IFloorPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FloorPlanServiceImpl implements IFloorPlanService {

    private final CafeRepository cafeRepository;
    private final CafeTableRepository tableRepository;
    private final FloorPlanRepository planRepository;
    private final FloorObstacleRepository obstacleRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<List<FloorPlan>> getPlansBySlug(String slug) {
        return cafeRepository.findBySlug(slug)
                .map(cafe -> planRepository.findByCafeIdOrderBySortOrderAsc(cafe.getId().toString()));
    }

    @Override
    @Transactional
    public Optional<FloorPlan> createPlan(String slug, FloorPlan input) {
        Optional<Cafe> cafe = cafeRepository.findBySlug(slug);
        if (cafe.isEmpty()) return Optional.empty();
        FloorPlan plan = FloorPlan.builder()
                .cafeId(cafe.get().getId().toString())
                .name(cleanName(input.getName()))
                .width(clamp(input.getWidth(), 4, 50, 12))
                .height(clamp(input.getHeight(), 4, 50, 8))
                .sortOrder(planRepository.findByCafeIdOrderBySortOrderAsc(cafe.get().getId().toString()).size())
                .build();
        return Optional.of(planRepository.save(plan));
    }

    @Override
    @Transactional
    public Optional<FloorPlan> updatePlan(String id, FloorPlan input) {
        return planRepository.findById(id).map(plan -> {
            plan.setName(cleanName(input.getName()));
            plan.setWidth(clamp(input.getWidth(), 4, 50, plan.getWidth()));
            plan.setHeight(clamp(input.getHeight(), 4, 50, plan.getHeight()));
            return planRepository.save(plan);
        });
    }

    @Override
    @Transactional
    public boolean deletePlan(String id) {
        if (!planRepository.existsById(id)) return false;
        obstacleRepository.deleteByFloorPlanId(id);
        tableRepository.deleteAll(tableRepository.findByFloorPlanId(id));
        planRepository.deleteById(id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<List<FloorObstacle>> getObstacles(String id) {
        if (!planRepository.existsById(id)) return Optional.empty();
        return Optional.of(obstacleRepository.findByFloorPlanId(id));
    }

    @Override
    @Transactional
    public Optional<List<FloorObstacle>> saveObstacles(String id, List<FloorObstacle> input) {
        if (!planRepository.existsById(id)) return Optional.empty();
        obstacleRepository.deleteByFloorPlanId(id);
        List<FloorObstacle> clean = input.stream().map(item -> FloorObstacle.builder()
                .floorPlanId(id)
                .label(cleanLabel(item.getLabel()))
                .posX(clampDouble(item.getPosX(), 0, 100, 40))
                .posY(clampDouble(item.getPosY(), 0, 100, 40))
                .width(clampDouble(item.getWidth(), 3, 100, 30))
                .height(clampDouble(item.getHeight(), 3, 100, 3))
                .build()).toList();
        return Optional.of(obstacleRepository.saveAll(clean));
    }

    @Override
    @Transactional
    public Optional<List<CafeTable>> savePlanTables(String id, List<CafeTable> input) {
        Optional<FloorPlan> planOpt = planRepository.findById(id);
        if (planOpt.isEmpty()) return Optional.empty();
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
        return Optional.of(tableRepository.saveAll(clean));
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
