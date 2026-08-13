package com.taktak.controller;

import com.taktak.model.ServiceCall;
import com.taktak.service.IServiceCallService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServiceCallController {

    private final IServiceCallService serviceCallService;

    @Data
    public static class CreateServiceCallPayload {
        private Integer tableNumber;
        private String type; // "BILL" or "WAITER"
        private String paymentMethod; // "CASH" or "CARD"
    }

    @PostMapping("/cafes/{slug}/service-calls")
    public ResponseEntity<ServiceCall> createServiceCall(
            @PathVariable String slug,
            @RequestBody CreateServiceCallPayload payload) {
        return ResponseEntity.ok(serviceCallService.createServiceCall(slug, payload));
    }

    @GetMapping("/cafes/{slug}/service-calls")
    public ResponseEntity<List<ServiceCall>> getActiveServiceCalls(@PathVariable String slug) {
        return ResponseEntity.ok(serviceCallService.getActiveServiceCalls(slug));
    }

    @PatchMapping("/service-calls/{id}/dismiss")
    public ResponseEntity<ServiceCall> dismissServiceCall(@PathVariable UUID id) {
        return serviceCallService.dismissServiceCall(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
