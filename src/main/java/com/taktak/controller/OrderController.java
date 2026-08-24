package com.taktak.controller;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.OrderStatusDto;
import com.taktak.dto.TableTransferDto;
import com.taktak.model.Order;
import com.taktak.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final IOrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderPayload payload, jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        Order created = orderService.createOrder(payload, clientIp);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/cafes/{slug}/staff-heartbeat")
    public ResponseEntity<Map<String, String>> staffHeartbeat(@PathVariable String slug, jakarta.servlet.http.HttpServletRequest request) {
        String staffIp = extractClientIp(request);
        orderService.updateCafeWifiIp(slug, staffIp);
        return ResponseEntity.ok(Map.of("status", "ok", "wifiIp", staffIp));
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @GetMapping("/cafes/{slug}/orders")
    public ResponseEntity<List<Order>> getOrdersForCafe(@PathVariable String slug) {
        List<Order> orders = orderService.getOrdersForCafe(slug);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @DeleteMapping("/cafes/{slug}/orders/in-progress")
    public ResponseEntity<Map<String, Integer>> archiveInProgressOrdersLegacy(@PathVariable String slug) {
        int archived = orderService.archiveInProgressOrders(slug);
        return ResponseEntity.ok(Map.of("archivedOrders", archived));
    }

    @PostMapping("/cafes/{slug}/orders/in-progress/archive")
    public ResponseEntity<Map<String, Integer>> archiveInProgressOrders(@PathVariable String slug) {
        int archived = orderService.archiveInProgressOrders(slug);
        return ResponseEntity.ok(Map.of("archivedOrders", archived));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable UUID id, @RequestBody OrderStatusDto dto) {
        Order updated = orderService.updateOrderStatus(id, dto.getStatus());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/cafes/{slug}/orders/{id}/transfer-table")
    public ResponseEntity<List<Order>> transferOrderTable(
            @PathVariable String slug,
            @PathVariable UUID id,
            @RequestBody TableTransferDto dto) {
        return ResponseEntity.ok(orderService.transferOrderTable(id, slug, dto));
    }
}
