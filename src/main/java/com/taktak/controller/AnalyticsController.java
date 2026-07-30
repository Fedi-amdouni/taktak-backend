package com.taktak.controller;

import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderService orderService;

    @GetMapping("/cafes/{cafeSlug}/analytics/waiters")
    public ResponseEntity<List<WaiterPerformanceDto>> getWaiterPerformance(
            @PathVariable String cafeSlug,
            @RequestParam(defaultValue = "TODAY") String period
    ) {
        return ResponseEntity.ok(orderService.getWaiterPerformanceMetrics(cafeSlug, period));
    }
}
