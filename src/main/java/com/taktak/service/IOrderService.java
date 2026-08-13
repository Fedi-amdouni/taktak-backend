package com.taktak.service;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.model.Order;
import com.taktak.model.OrderStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IOrderService {
    Order createOrder(CreateOrderPayload payload);
    List<Order> getOrdersForCafe(String cafeSlug);
    Order updateOrderStatus(UUID orderId, OrderStatus newStatus);
    Order transferOrderTable(UUID orderId, Integer newTableNumber);
    int deleteInProgressOrders(String cafeSlug);
    Map<String, Object> getAnalyticsForCafe(String cafeSlug);
    List<WaiterPerformanceDto> getWaiterPerformanceMetrics(String cafeSlug, String period);
}
