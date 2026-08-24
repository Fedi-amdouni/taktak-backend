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
    Order createOrder(CreateOrderPayload payload, String clientIp);
    void updateCafeWifiIp(String cafeSlug, String staffIp);
    List<Order> getOrdersForCafe(String cafeSlug);
    Order getOrder(UUID orderId);
    Order updateOrderStatus(UUID orderId, OrderStatus newStatus);
    Order transferOrderTable(UUID orderId, Integer newTableNumber);
    int archiveInProgressOrders(String cafeSlug);
    Map<String, Object> getAnalyticsForCafe(String cafeSlug);
    List<WaiterPerformanceDto> getWaiterPerformanceMetrics(String cafeSlug, String period);
}
