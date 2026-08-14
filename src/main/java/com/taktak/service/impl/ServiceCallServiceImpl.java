package com.taktak.service.impl;

import com.taktak.controller.ServiceCallController.CreateServiceCallPayload;
import com.taktak.model.Cafe;
import com.taktak.model.ServiceCall;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.ServiceCallRepository;
import com.taktak.service.IServiceCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServiceCallServiceImpl implements IServiceCallService {

    private final ServiceCallRepository serviceCallRepository;
    private final CafeRepository cafeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public ServiceCall createServiceCall(String slug, CreateServiceCallPayload payload) {
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

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ServiceCall> getActiveServiceCalls(String slug) {
        Cafe cafe = cafeRepository.findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));
        return serviceCallRepository.findByCafeIdAndActiveTrueOrderByCreatedAtDesc(cafe.getId());
    }

    @Override
    @Transactional
    public Optional<ServiceCall> dismissServiceCall(UUID id) {
        return serviceCallRepository.findById(id).map(call -> {
            call.setActive(false);
            return serviceCallRepository.save(call);
        });
    }
}
