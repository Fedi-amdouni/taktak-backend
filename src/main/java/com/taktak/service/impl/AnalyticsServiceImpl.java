package com.taktak.service.impl;

import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.service.IAnalyticsService;
import com.taktak.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements IAnalyticsService {

    private final IOrderService orderService;

    @Override
    public List<WaiterPerformanceDto> getWaiterPerformanceMetrics(String cafeSlug, String period) {
        return orderService.getWaiterPerformanceMetrics(cafeSlug, period);
    }
}
