package com.taktak.service;

import com.taktak.dto.WaiterPerformanceDto;

import java.util.List;

public interface IAnalyticsService {
    List<WaiterPerformanceDto> getWaiterPerformanceMetrics(String cafeSlug, String period);
}
