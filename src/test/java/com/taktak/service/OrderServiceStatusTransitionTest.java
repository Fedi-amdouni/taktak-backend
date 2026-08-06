package com.taktak.service;

import com.taktak.model.Cafe;
import com.taktak.model.Order;
import com.taktak.model.OrderStatus;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.TableAssignmentRepository;
import com.taktak.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceStatusTransitionTest {

    private OrderRepository orderRepository;
    private CafeRepository cafeRepository;
    private SimpMessagingTemplate messagingTemplate;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cafeRepository = mock(CafeRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        orderService = new OrderService(
                orderRepository,
                cafeRepository,
                mock(WaiterRepository.class),
                mock(TableAssignmentRepository.class),
                messagingTemplate
        );
    }

    @ParameterizedTest(name = "{0} -> {1} est autorisée")
    @MethodSource("allowedTransitions")
    void acceptsEveryAllowedTransition(OrderStatus current, OrderStatus next) {
        UUID orderId = UUID.randomUUID();
        UUID cafeId = UUID.randomUUID();
        Order order = order(orderId, cafeId, current);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));

        Order updated = orderService.updateOrderStatus(orderId, next);

        assertSame(order, updated);
        assertEquals(next, updated.getStatus());
        verify(orderRepository).save(order);
        verify(messagingTemplate).convertAndSend("/topic/orders/monastir-lounge", order);
    }

    @Test
    void rejectsEveryInvalidTransitionWithConflict() {
        for (OrderStatus current : workflowStatuses()) {
            for (OrderStatus requested : workflowStatuses()) {
                if (requested == current || isAllowed(current, requested)) {
                    continue;
                }

                UUID orderId = UUID.randomUUID();
                Order order = order(orderId, UUID.randomUUID(), current);
                when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

                ResponseStatusException error = assertThrows(
                        ResponseStatusException.class,
                        () -> orderService.updateOrderStatus(orderId, requested),
                        current + " -> " + requested + " aurait dû être refusée"
                );

                assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
                assertEquals(current, order.getStatus());
            }
        }

        verify(orderRepository, never()).save(any(Order.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Order.class));
    }

    @Test
    void repeatingTheCurrentStatusIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, UUID.randomUUID(), OrderStatus.READY);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        Order unchanged = orderService.updateOrderStatus(orderId, OrderStatus.READY);

        assertSame(order, unchanged);
        assertEquals(OrderStatus.READY, unchanged.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Order.class));
    }

    @Test
    void missingOrderReturnsNotFoundInsteadOfAFakeOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.updateOrderStatus(orderId, OrderStatus.PREPARING)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.RECEIVED, OrderStatus.PREPARING),
                Arguments.of(OrderStatus.RECEIVED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PREPARING, OrderStatus.READY),
                Arguments.of(OrderStatus.READY, OrderStatus.PICKED_UP),
                Arguments.of(OrderStatus.PICKED_UP, OrderStatus.SERVED),
                Arguments.of(OrderStatus.SERVED, OrderStatus.PAID),
                Arguments.of(OrderStatus.PAID, OrderStatus.ARCHIVED)
        );
    }

    private static OrderStatus[] workflowStatuses() {
        return Arrays.stream(OrderStatus.values())
                .filter(status -> status != OrderStatus.CANCELLED)
                .toArray(OrderStatus[]::new);
    }

    private static boolean isAllowed(OrderStatus current, OrderStatus requested) {
        return allowedTransitions().anyMatch(arguments ->
                arguments.get()[0] == current && arguments.get()[1] == requested
        );
    }

    private static Order order(UUID id, UUID cafeId, OrderStatus status) {
        return Order.builder()
                .id(id)
                .cafeId(cafeId)
                .tableId(UUID.randomUUID())
                .tableNumber(5)
                .status(status)
                .totalPrice(BigDecimal.TEN)
                .items(new ArrayList<>())
                .build();
    }
}
