package com.taktak.controller;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.OrderStatusDto;
import com.taktak.dto.TableTransferDto;
import com.taktak.model.Order;
import com.taktak.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/orders")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderPayload payload) {
        Order created = orderService.createOrder(payload);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/cafes/{slug}/orders")
    public ResponseEntity<List<Order>> getOrdersForCafe(@PathVariable String slug) {
        List<Order> orders = orderService.getOrdersForCafe(slug);
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable UUID id, @RequestBody OrderStatusDto dto) {
        Order updated = orderService.updateOrderStatus(id, dto.getStatus());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/orders/{id}/transfer-table")
    public ResponseEntity<Order> transferOrderTable(@PathVariable UUID id, @RequestBody TableTransferDto dto) {
        Order updated = orderService.transferOrderTable(id, dto.getNewTableNumber());
        return ResponseEntity.ok(updated);
    }
}
