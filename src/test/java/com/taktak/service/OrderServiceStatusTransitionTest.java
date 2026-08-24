package com.taktak.service;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.TableTransferDto;
import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.model.Order;
import com.taktak.model.OrderPresenceStatus;
import com.taktak.model.OrderStatus;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.ProductRepository;
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

import com.taktak.service.impl.OrderServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeastOnce;

class OrderServiceStatusTransitionTest {

    private OrderRepository orderRepository;
    private CafeRepository cafeRepository;
    private CafeTableRepository cafeTableRepository;
    private SimpMessagingTemplate messagingTemplate;
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        cafeRepository = mock(CafeRepository.class);
        cafeTableRepository = mock(CafeTableRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        orderService = new OrderServiceImpl(
                orderRepository,
                mock(ProductRepository.class),
                cafeRepository,
                cafeTableRepository,
                mock(WaiterRepository.class),
                mock(TableAssignmentRepository.class),
                messagingTemplate
        );
    }

    @Test
    void transfersEveryActiveOrderOwnedByTheParticipantToAValidatedTargetTable() {
        UUID cafeId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();
        Order anchor = order(anchorId, cafeId, OrderStatus.RECEIVED);
        anchor.setTableNumber(5);
        anchor.setParticipantId("participant-1");
        Order second = order(UUID.randomUUID(), cafeId, OrderStatus.PREPARING);
        second.setTableNumber(5);
        second.setParticipantId("participant-1");

        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();
        CafeTable sourceTable = new CafeTable();
        sourceTable.setCafeId(cafeId.toString());
        sourceTable.setTableNumber(5);
        sourceTable.setSessionToken("source-token");
        CafeTable targetTable = new CafeTable();
        targetTable.setCafeId(cafeId.toString());
        targetTable.setTableNumber(8);
        targetTable.setSessionToken("target-token");

        TableTransferDto request = new TableTransferDto();
        request.setSourceTableNumber(5);
        request.setNewTableNumber(8);
        request.setParticipantId("participant-1");
        request.setSourceSessionToken("source-token");
        request.setTargetSessionToken("target-token");

        when(orderRepository.findByIdForUpdate(anchorId)).thenReturn(Optional.of(anchor));
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 5)).thenReturn(Optional.of(sourceTable));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 8)).thenReturn(Optional.of(targetTable));
        when(orderRepository.findByCafeIdAndTableNumberAndParticipantIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(cafeId),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq("participant-1"),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of(anchor, second));
        when(orderRepository.saveAll(List.of(anchor, second))).thenReturn(List.of(anchor, second));

        List<Order> transferred = orderService.transferOrderTable(anchorId, "monastir-lounge", request);

        assertEquals(2, transferred.size());
        assertEquals(8, anchor.getTableNumber());
        assertEquals(8, second.getTableNumber());
        assertTrue(anchor.getTableChangedAlert());
        assertTrue(second.getTableChangedAlert());
    }

    @Test
    void rejectsTransferWhenTheTargetQrTokenDoesNotMatch() {
        UUID cafeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order anchor = order(orderId, cafeId, OrderStatus.RECEIVED);
        anchor.setTableNumber(5);
        anchor.setParticipantId("participant-1");
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").build();
        CafeTable targetTable = new CafeTable();
        targetTable.setCafeId(cafeId.toString());
        targetTable.setTableNumber(8);
        targetTable.setSessionToken("real-target-token");

        TableTransferDto request = transferRequest("wrong-target-token");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(anchor));
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 8))
                .thenReturn(Optional.of(targetTable));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.transferOrderTable(orderId, "monastir-lounge", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(5, anchor.getTableNumber());
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void rejectsIdempotentRetryWhenTheSourceQrTokenDoesNotMatch() {
        UUID cafeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order anchor = order(orderId, cafeId, OrderStatus.RECEIVED);
        anchor.setTableNumber(8);
        anchor.setParticipantId("participant-1");
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").build();
        CafeTable sourceTable = new CafeTable();
        sourceTable.setCafeId(cafeId.toString());
        sourceTable.setTableNumber(5);
        sourceTable.setSessionToken("real-source-token");
        CafeTable targetTable = new CafeTable();
        targetTable.setCafeId(cafeId.toString());
        targetTable.setTableNumber(8);
        targetTable.setSessionToken("target-token");

        TableTransferDto request = transferRequest("target-token");
        request.setSourceSessionToken("wrong-source-token");
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(anchor));
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 5))
                .thenReturn(Optional.of(sourceTable));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 8))
                .thenReturn(Optional.of(targetTable));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.transferOrderTable(orderId, "monastir-lounge", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void returnsNotFoundInsteadOfInventingAnOrderDuringTransfer() {
        UUID cafeId = UUID.randomUUID();
        UUID missingOrderId = UUID.randomUUID();
        when(cafeRepository.findBySlug("monastir-lounge"))
                .thenReturn(Optional.of(Cafe.builder().id(cafeId).slug("monastir-lounge").build()));
        when(orderRepository.findByIdForUpdate(missingOrderId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.transferOrderTable(
                        missingOrderId,
                        "monastir-lounge",
                        transferRequest("target-token")
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void rejectsTransferOfATerminalOrder() {
        UUID cafeId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order paidOrder = order(orderId, cafeId, OrderStatus.PAID);
        paidOrder.setTableNumber(5);
        paidOrder.setParticipantId("participant-1");
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").build();
        CafeTable targetTable = new CafeTable();
        targetTable.setCafeId(cafeId.toString());
        targetTable.setTableNumber(8);
        targetTable.setSessionToken("target-token");

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(paidOrder));
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 8))
                .thenReturn(Optional.of(targetTable));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.transferOrderTable(
                        orderId,
                        "monastir-lounge",
                        transferRequest("target-token")
                )
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertEquals(5, paidOrder.getTableNumber());
        verify(orderRepository, never()).saveAll(any());
    }

    private TableTransferDto transferRequest(String targetToken) {
        TableTransferDto request = new TableTransferDto();
        request.setSourceTableNumber(5);
        request.setNewTableNumber(8);
        request.setParticipantId("participant-1");
        request.setSourceSessionToken("source-token");
        request.setTargetSessionToken(targetToken);
        return request;
    }

    @ParameterizedTest(name = "{0} -> {1} est autorisée")
    @MethodSource("allowedTransitions")
    void acceptsEveryAllowedTransition(OrderStatus current, OrderStatus next) {
        UUID orderId = UUID.randomUUID();
        UUID cafeId = UUID.randomUUID();
        Order order = order(orderId, cafeId, current);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();

        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));

        Order updated = orderService.updateOrderStatus(orderId, next);

        assertSame(order, updated);
        assertEquals(next, updated.getStatus());
        verify(orderRepository).save(order);
        verify(messagingTemplate).convertAndSend("/topic/orders/monastir-lounge", order);
    }

    @Test
    void statusUpdateUsesTheLatestLockedOrderAfterAConcurrentTableTransfer() {
        UUID orderId = UUID.randomUUID();
        UUID cafeId = UUID.randomUUID();
        Order staleOrder = order(orderId, cafeId, OrderStatus.RECEIVED);
        staleOrder.setTableNumber(5);
        Order lockedOrder = order(orderId, cafeId, OrderStatus.PREPARING);
        lockedOrder.setTableNumber(8);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(staleOrder));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(lockedOrder));
        when(orderRepository.save(lockedOrder)).thenReturn(lockedOrder);
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));

        Order updated = orderService.updateOrderStatus(orderId, OrderStatus.READY);

        assertSame(lockedOrder, updated);
        assertEquals(8, updated.getTableNumber());
        assertEquals(OrderStatus.READY, updated.getStatus());
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
                when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

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
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        Order unchanged = orderService.updateOrderStatus(orderId, OrderStatus.READY);

        assertSame(order, unchanged);
        assertEquals(OrderStatus.READY, unchanged.getStatus());
        verify(orderRepository, never()).save(any(Order.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Order.class));
    }

    @Test
    void missingOrderReturnsNotFoundInsteadOfAFakeOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> orderService.updateOrderStatus(orderId, OrderStatus.PREPARING)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void repeatingTheSameClientOrderReturnsTheExistingOrder() {
        UUID cafeId = UUID.randomUUID();
        String clientOrderId = UUID.randomUUID().toString();
        String participantId = UUID.randomUUID().toString();
        Cafe cafe = cafe(cafeId);
        Order existing = order(UUID.randomUUID(), cafeId, OrderStatus.RECEIVED);
        existing.setClientOrderId(clientOrderId);
        existing.setParticipantId(participantId);

        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orderRepository.findByCafeIdAndClientOrderId(cafeId, clientOrderId)).thenReturn(Optional.of(existing));

        Order result = orderService.createOrder(createPayload(clientOrderId, participantId));

        assertSame(existing, result);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Order.class));
    }

    @Test
    void aNewClientOrderIdCreatesAnAdditionalOrderForTheSameParticipantAndTable() {
        UUID cafeId = UUID.randomUUID();
        String participantId = UUID.randomUUID().toString();
        Cafe cafe = cafe(cafeId);

        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orderRepository.findByCafeIdAndClientOrderId(any(UUID.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order first = orderService.createOrder(createPayload(UUID.randomUUID().toString(), participantId));
        Order additional = orderService.createOrder(createPayload(UUID.randomUUID().toString(), participantId));

        assertEquals(participantId, first.getParticipantId());
        assertEquals(participantId, additional.getParticipantId());
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
        verify(messagingTemplate, times(2)).convertAndSend(any(String.class), any(Order.class));
    }

    @Test
    void archivesOnlyInProgressOrdersForTheRequestedCafeWithoutDeletingHistory() {
        UUID cafeId = UUID.randomUUID();
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();
        Order received = order(UUID.randomUUID(), cafeId, OrderStatus.RECEIVED);
        Order ready = order(UUID.randomUUID(), cafeId, OrderStatus.READY);

        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orderRepository.findByCafeIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(cafeId),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(List.of(received, ready));
        when(orderRepository.saveAll(List.of(received, ready))).thenReturn(List.of(received, ready));

        int archived = orderService.archiveInProgressOrders("monastir-lounge");

        assertEquals(2, archived);
        verify(orderRepository).saveAll(List.of(received, ready));
        verify(orderRepository, never()).deleteAll(any());
        assertEquals(OrderStatus.ARCHIVED, received.getStatus());
        assertEquals(OrderStatus.ARCHIVED, ready.getStatus());
        assertEquals("STAFF_BULK_ARCHIVE", received.getArchiveReason());
        assertNotNull(received.getArchivedAt());
        verify(messagingTemplate).convertAndSend("/topic/orders/monastir-lounge", received);
        verify(messagingTemplate).convertAndSend("/topic/orders/monastir-lounge", ready);
    }

    @Test
    void archivesPaidOrdersOlderThanTheConfiguredCutoff() {
        UUID cafeId = UUID.randomUUID();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        Order paid = order(UUID.randomUUID(), cafeId, OrderStatus.PAID);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();

        when(orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PAID, cutoff)).thenReturn(List.of(paid));
        when(orderRepository.saveAll(List.of(paid))).thenReturn(List.of(paid));
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));

        int archived = orderService.archivePaidOrdersBefore(cutoff);

        assertEquals(1, archived);
        assertEquals(OrderStatus.ARCHIVED, paid.getStatus());
        assertEquals("PAID_RETENTION_ELAPSED", paid.getArchiveReason());
        assertNotNull(paid.getArchivedAt());
        verify(orderRepository).saveAll(List.of(paid));
        verify(messagingTemplate).convertAndSend("/topic/orders/monastir-lounge", paid);
    }

    @Test
    void normalLifecycleReleasesTableOnlyAfterArchive() {
        UUID cafeId = UUID.randomUUID();
        Order order = order(UUID.randomUUID(), cafeId, OrderStatus.RECEIVED);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();
        CafeTable table = new CafeTable();
        table.setCafeId(cafeId.toString());
        table.setTableNumber(5);
        table.setSessionToken("original-session");

        when(orderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));
        when(orderRepository.existsByCafeIdAndTableNumberAndStatusNotIn(
                org.mockito.ArgumentMatchers.eq(cafeId),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(false);
        when(cafeTableRepository.findByCafeIdAndTableNumber(cafeId.toString(), 5)).thenReturn(Optional.of(table));

        for (OrderStatus next : List.of(
                OrderStatus.PREPARING,
                OrderStatus.READY,
                OrderStatus.SERVED,
                OrderStatus.PAID
        )) {
            orderService.updateOrderStatus(order.getId(), next);
        }

        assertEquals("original-session", table.getSessionToken(), "le paiement seul ne libère pas la table");
        verify(cafeTableRepository, never()).save(any(CafeTable.class));

        orderService.updateOrderStatus(order.getId(), OrderStatus.ARCHIVED);

        assertEquals(OrderStatus.ARCHIVED, order.getStatus());
        assertNotNull(order.getArchivedAt());
        assertNotEquals("original-session", table.getSessionToken());
        verify(cafeTableRepository).save(table);
    }

    @Test
    void archiveDoesNotReleaseTableWhileAnotherOrderIsNotTerminal() {
        UUID cafeId = UUID.randomUUID();
        Order paid = order(UUID.randomUUID(), cafeId, OrderStatus.PAID);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();

        when(orderRepository.findByIdForUpdate(paid.getId())).thenReturn(Optional.of(paid));
        when(orderRepository.save(paid)).thenReturn(paid);
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));
        when(orderRepository.existsByCafeIdAndTableNumberAndStatusNotIn(
                org.mockito.ArgumentMatchers.eq(cafeId),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(true);

        orderService.updateOrderStatus(paid.getId(), OrderStatus.ARCHIVED);

        verify(cafeTableRepository, never()).findByCafeIdAndTableNumber(any(), any());
        verify(cafeTableRepository, never()).save(any(CafeTable.class));
    }

    @Test
    void archivesStaleActiveOrdersSafelyAndTraceably() {
        UUID cafeId = UUID.randomUUID();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        Order stale = order(UUID.randomUUID(), cafeId, OrderStatus.PREPARING);
        Cafe cafe = Cafe.builder().id(cafeId).slug("monastir-lounge").name("Monastir Lounge").build();

        when(orderRepository.findByStatusInAndUpdatedAtBefore(any(), org.mockito.ArgumentMatchers.eq(cutoff)))
                .thenReturn(List.of(stale));
        when(orderRepository.saveAll(List.of(stale))).thenReturn(List.of(stale));
        when(cafeRepository.findById(cafeId)).thenReturn(Optional.of(cafe));
        when(orderRepository.existsByCafeIdAndTableNumberAndStatusNotIn(any(), any(), any())).thenReturn(false);

        int archived = orderService.archiveStaleActiveOrdersBefore(cutoff);

        assertEquals(1, archived);
        assertEquals(OrderStatus.ARCHIVED, stale.getStatus());
        assertEquals("STALE_ACTIVE_ORDER", stale.getArchiveReason());
        assertNotNull(stale.getArchivedAt());
        verify(orderRepository).saveAll(List.of(stale));
        verify(orderRepository, never()).deleteAll(any());
    }

    @Test
    void twoDevicesCanCreateSeparateOrdersOnTheSameTable() {
        UUID cafeId = UUID.randomUUID();
        Cafe cafe = cafe(cafeId);
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orderRepository.findByCafeIdAndClientOrderId(any(UUID.class), any(String.class)))
                .thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order phoneA = orderService.createOrder(createPayload(UUID.randomUUID().toString(), "phone-a"));
        Order phoneB = orderService.createOrder(createPayload(UUID.randomUUID().toString(), "phone-b"));

        assertEquals(5, phoneA.getTableNumber());
        assertEquals(5, phoneB.getTableNumber());
        assertNotEquals(phoneA.getParticipantId(), phoneB.getParticipantId());
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
    }

    @Test
    void verifiesAcceptedRefusedInaccurateGpsAndOfficialWifi() {
        UUID cafeId = UUID.randomUUID();
        Cafe cafe = cafe(cafeId);
        cafe.setLastKnownWifiIp("203.0.113.10");
        when(cafeRepository.findBySlug("monastir-lounge")).thenReturn(Optional.of(cafe));
        when(orderRepository.findByCafeIdAndClientOrderId(any(UUID.class), any(String.class))).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderPayload accepted = createPayload(UUID.randomUUID().toString(), "gps-ok");
        accepted.setClientLatitude(35.777);
        accepted.setClientLongitude(10.826);
        accepted.setClientAccuracyMeters(10.0);
        assertEquals(OrderPresenceStatus.VERIFIED_GPS, orderService.createOrder(accepted, "198.51.100.1").getPresenceStatus());

        CreateOrderPayload refused = createPayload(UUID.randomUUID().toString(), "gps-refused");
        assertEquals(OrderPresenceStatus.UNVERIFIED_LOCATION, orderService.createOrder(refused, "198.51.100.2").getPresenceStatus());

        CreateOrderPayload inaccurate = createPayload(UUID.randomUUID().toString(), "gps-inaccurate");
        inaccurate.setClientLatitude(35.777);
        inaccurate.setClientLongitude(10.826);
        inaccurate.setClientAccuracyMeters(80.0);
        assertEquals(OrderPresenceStatus.UNVERIFIED_LOCATION, orderService.createOrder(inaccurate, "198.51.100.3").getPresenceStatus());

        CreateOrderPayload wifi = createPayload(UUID.randomUUID().toString(), "wifi");
        assertEquals(OrderPresenceStatus.VERIFIED_WIFI, orderService.createOrder(wifi, "203.0.113.10").getPresenceStatus());
    }

    private static Stream<Arguments> allowedTransitions() {
        return Stream.of(
                Arguments.of(OrderStatus.RECEIVED, OrderStatus.PREPARING),
                Arguments.of(OrderStatus.RECEIVED, OrderStatus.CANCELLED),
                Arguments.of(OrderStatus.PREPARING, OrderStatus.READY),
                Arguments.of(OrderStatus.READY, OrderStatus.SERVED),
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

    private static Cafe cafe(UUID cafeId) {
        return Cafe.builder()
                .id(cafeId)
                .slug("monastir-lounge")
                .name("Monastir Lounge")
                .latitude(35.777)
                .longitude(10.826)
                .geofenceRadiusMeters(120.0)
                .build();
    }

    private static CreateOrderPayload createPayload(String clientOrderId, String participantId) {
        CreateOrderPayload payload = new CreateOrderPayload();
        payload.setCafeSlug("monastir-lounge");
        payload.setTableNumber(5);
        payload.setTotalPrice(BigDecimal.TEN);
        payload.setClientOrderId(clientOrderId);
        payload.setParticipantId(participantId);
        payload.setItems(List.of());
        return payload;
    }
}
