package com.taktak.controller;

import com.taktak.model.CafeTable;
import com.taktak.model.FloorPlan;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.FloorObstacleRepository;
import com.taktak.repository.FloorPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class FloorPlanControllerTest {
    private CafeTableRepository tableRepository;
    private FloorPlanRepository planRepository;
    private FloorPlanController controller;

    @BeforeEach
    void setUp() {
        tableRepository = mock(CafeTableRepository.class);
        planRepository = mock(FloorPlanRepository.class);
        controller = new FloorPlanController(
                mock(CafeRepository.class), tableRepository, planRepository, mock(FloorObstacleRepository.class)
        );
    }

    @Test
    void normalizesTableCodeAndLinksTableToPlan() {
        FloorPlan plan = FloorPlan.builder().id("plan-1").cafeId("cafe-1").name("Terrasse").build();
        CafeTable table = new CafeTable();
        table.setId("temp_1");
        table.setTableNumber(5);
        table.setTableCode("t5");
        table.setPosX(50.0);
        table.setPosY(50.0);

        when(planRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(tableRepository.findByFloorPlanId("plan-1")).thenReturn(List.of());
        when(tableRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<CafeTable> saved = controller.savePlanTables("plan-1", List.of(table)).getBody();

        assertNotNull(saved);
        assertEquals("T05", saved.get(0).getTableCode());
        assertEquals("plan-1", saved.get(0).getFloorPlanId());
        assertEquals("Terrasse", saved.get(0).getZoneName());
        assertEquals("cafe-1", saved.get(0).getCafeId());
        assertNull(saved.get(0).getId());
    }

    @Test
    void deletingPlanAlsoDeletesItsObstaclesAndTables() {
        FloorObstacleRepository obstacleRepository = mock(FloorObstacleRepository.class);
        controller = new FloorPlanController(mock(CafeRepository.class), tableRepository, planRepository, obstacleRepository);
        CafeTable table = new CafeTable();
        table.setId("table-1");
        when(planRepository.existsById("plan-1")).thenReturn(true);
        when(tableRepository.findByFloorPlanId("plan-1")).thenReturn(List.of(table));

        assertEquals(204, controller.deletePlan("plan-1").getStatusCode().value());
        verify(obstacleRepository).deleteByFloorPlanId("plan-1");
        verify(tableRepository).deleteAll(List.of(table));
        verify(planRepository).deleteById("plan-1");
    }
}
