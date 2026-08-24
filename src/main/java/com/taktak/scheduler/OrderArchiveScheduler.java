package com.taktak.scheduler;

import com.taktak.service.impl.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "taktak.orders.archive-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderArchiveScheduler {

    private final OrderServiceImpl orderService;

    @Scheduled(fixedDelayString = "${taktak.orders.archive-check-ms:5000}")
    public void archiveOrders() {
        orderService.archivePaidOrders();
    }
}
