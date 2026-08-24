package com.taktak.scheduler;

import com.taktak.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderArchiveSchedulerTest {

    @Test
    void delegatesArchiveWorkToOrderService() {
        OrderServiceImpl orderService = mock(OrderServiceImpl.class);
        OrderArchiveScheduler scheduler = new OrderArchiveScheduler(orderService);

        scheduler.archiveOrders();

        verify(orderService).archivePaidOrders();
    }
}
