package com.taktak.controller;

import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.service.IAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AnalyticsController {

    private final IAnalyticsService analyticsService;

    @GetMapping("/cafes/{cafeSlug}/analytics/waiters")
    public ResponseEntity<List<WaiterPerformanceDto>> getWaiterPerformance(
            @PathVariable String cafeSlug,
            @RequestParam(defaultValue = "TODAY") String period
    ) {
        return ResponseEntity.ok(analyticsService.getWaiterPerformanceMetrics(cafeSlug, period));
    }
}
