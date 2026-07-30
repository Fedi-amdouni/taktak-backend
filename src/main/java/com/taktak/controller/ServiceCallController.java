package com.taktak.controller;

import com.taktak.model.Cafe;
import com.taktak.model.ServiceCall;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.ServiceCallRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServiceCallController {

    private final ServiceCallRepository serviceCallRepository;
    private final CafeRepository cafeRepository;
    private final SimpMessagingTemplate messagingTemplate;

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
        
        Cafe cafe = cafeRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));

        ServiceCall call = ServiceCall.builder()
                .cafeId(cafe.getId())
                .tableNumber(payload.getTableNumber())
                .type(payload.getType())
                .paymentMethod(payload.getPaymentMethod())
                .active(true)
                .build();

        ServiceCall saved = serviceCallRepository.save(call);

        // Broadcast to tablet staff WebSocket channel
        messagingTemplate.convertAndSend("/topic/service-calls/" + slug, saved);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/cafes/{slug}/service-calls")
    public ResponseEntity<List<ServiceCall>> getActiveServiceCalls(@PathVariable String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));
        return ResponseEntity.ok(serviceCallRepository.findByCafeIdAndActiveTrueOrderByCreatedAtDesc(cafe.getId()));
    }

    @PatchMapping("/service-calls/{id}/dismiss")
    public ResponseEntity<ServiceCall> dismissServiceCall(@PathVariable UUID id) {
        return serviceCallRepository.findById(id).map(call -> {
            call.setActive(false);
            ServiceCall updated = serviceCallRepository.save(call);
            return ResponseEntity.ok(updated);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
