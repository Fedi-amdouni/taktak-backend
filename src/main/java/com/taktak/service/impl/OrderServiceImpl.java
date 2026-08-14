package com.taktak.service.impl;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.model.*;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.CouponRepository;
import com.taktak.repository.TableAssignmentRepository;
import com.taktak.repository.WaiterRepository;
import com.taktak.service.IOrderService;
import com.taktak.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements IOrderService {

    private static final Set<OrderStatus> IN_PROGRESS_STATUSES = EnumSet.of(
            OrderStatus.RECEIVED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.PICKED_UP
    );

    private static final Map<OrderStatus, OrderStatus> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.RECEIVED, OrderStatus.PREPARING,
            OrderStatus.PREPARING, OrderStatus.READY,
            OrderStatus.READY, OrderStatus.SERVED,
            OrderStatus.PICKED_UP, OrderStatus.SERVED,
            OrderStatus.SERVED, OrderStatus.PAID,
            OrderStatus.PAID, OrderStatus.ARCHIVED
    );

    private final OrderRepository orderRepository;
    private final CafeRepository cafeRepository;
    private final WaiterRepository waiterRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false) private RewardService rewardService;
    @Autowired(required = false) private CouponRepository couponRepository;

    @Value("${taktak.orders.auto-archive-minutes:5}")
    private long autoArchiveMinutes;

    @Override
    @Transactional
    public Order createOrder(CreateOrderPayload payload) {
        Cafe cafe = cafeRepository.findBySlug(payload.getCafeSlug())
                .orElseGet(() -> cafeRepository.save(
                        Cafe.builder()
                                .name("Monastir Lounge")
                                .slug(payload.getCafeSlug())
                                .build()
                ));

        BigDecimal subtotal = payload.getTotalPrice() != null ? payload.getTotalPrice() : BigDecimal.ZERO;
        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (payload.getCouponCode() != null && !payload.getCouponCode().isBlank()) {
            coupon = rewardService.requireUsable(cafe.getId(), payload.getCouponCode(), subtotal);
            discount = subtotal.multiply(coupon.getDiscountPercent()).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        }

        Order order = Order.builder()
                .cafeId(cafe.getId())
                .tableId(UUID.randomUUID())
                .tableNumber(payload.getTableNumber())
                .status(OrderStatus.RECEIVED)
                .totalPrice(subtotal.subtract(discount))
                .couponId(coupon != null ? coupon.getId() : null)
                .discountAmount(discount)
                .tipsAmount(BigDecimal.ZERO)
                .tableChangedAlert(false)
                .items(new ArrayList<>())
                .build();

        for (CreateOrderPayload.OrderItemPayload itemPayload : payload.getItems()) {
            String selJson = null;
            if (itemPayload.getSelectedOptions() != null && !itemPayload.getSelectedOptions().isEmpty()) {
                try {
                    selJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(itemPayload.getSelectedOptions());
                } catch (Exception ignored) {}
            }

            OrderItem item = OrderItem.builder()
                    .productId(itemPayload.getProductId())
                    .productName(itemPayload.getProductName())
                    .quantity(itemPayload.getQuantity() != null ? itemPayload.getQuantity() : 1)
                    .unitPrice(itemPayload.getUnitPrice() != null ? itemPayload.getUnitPrice() : BigDecimal.ZERO)
                    .selectedOptionsJson(selJson)
                    .notes(itemPayload.getNotes())
                    .build();
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);

        if (coupon != null) {
            coupon.setStatus(CouponStatus.RESERVED);
            coupon.setRedeemedOrderId(saved.getId());
            couponRepository.save(coupon);
        }

        // Broadcast to WebSocket subscribers on /topic/orders/{cafeSlug}
        messagingTemplate.convertAndSend("/topic/orders/" + payload.getCafeSlug(), saved);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersForCafe(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug).orElse(null);
        if (cafe == null) return List.of();
        return orderRepository.findByCafeIdOrderByCreatedAtDesc(cafe.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable"));
    }

    @Override
    @Transactional
    public Order updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable"));

        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le nouveau statut est obligatoire");
        }

        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == newStatus) {
            return order;
        }

        if (newStatus == OrderStatus.CANCELLED) {
            if (currentStatus != OrderStatus.RECEIVED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Impossible d'annuler une commande déjà en préparation ou servie"
                );
            }
        } else {
            OrderStatus expectedStatus = ALLOWED_TRANSITIONS.get(currentStatus);
            if (expectedStatus != newStatus) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Transition de statut invalide: " + currentStatus + " -> " + newStatus
                );
            }
        }

        LocalDateTime now = LocalDateTime.now();

        if (currentStatus == OrderStatus.RECEIVED && newStatus == OrderStatus.PREPARING
                && order.getAcceptedAt() == null) {
            order.setAcceptedAt(now);
        }

        if (newStatus == OrderStatus.SERVED && order.getServedAt() == null) {
            order.setServedAt(now);
            if (order.getAcceptedAt() == null) {
                order.setAcceptedAt(now);
            }
        }

        order.setStatus(newStatus);

        Order updated = orderRepository.save(order);

        if (couponRepository != null) couponRepository.findByRedeemedOrderId(updated.getId()).ifPresent(coupon -> {
            if (newStatus == OrderStatus.PAID && coupon.getStatus() == CouponStatus.RESERVED) {
                coupon.setStatus(CouponStatus.USED);
                coupon.setUsedAt(now);
                couponRepository.save(coupon);
            } else if (newStatus == OrderStatus.CANCELLED && coupon.getStatus() == CouponStatus.RESERVED) {
                coupon.setStatus(CouponStatus.ACTIVE);
                coupon.setRedeemedOrderId(null);
                couponRepository.save(coupon);
            }
        });

        if (updated.getItems() != null) {
            updated.getItems().size();
        }

        Cafe cafe = cafeRepository.findById(updated.getCafeId()).orElse(null);
        if (cafe != null) {
            messagingTemplate.convertAndSend("/topic/orders/" + cafe.getSlug(), updated);
        }

        return updated;
    }

    @Scheduled(fixedDelayString = "${taktak.orders.archive-check-ms:60000}")
    @Transactional
    public void archivePaidOrders() {
        archivePaidOrdersBefore(LocalDateTime.now().minusMinutes(autoArchiveMinutes));
    }

    public int archivePaidOrdersBefore(LocalDateTime cutoff) {
        List<Order> paidOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PAID, cutoff);
        if (paidOrders.isEmpty()) return 0;

        paidOrders.forEach(order -> order.setStatus(OrderStatus.ARCHIVED));
        List<Order> archivedOrders = orderRepository.saveAll(paidOrders);

        Map<UUID, String> cafeSlugs = new HashMap<>();
        archivedOrders.forEach(order -> {
            String cafeSlug = cafeSlugs.computeIfAbsent(order.getCafeId(), cafeId ->
                    cafeRepository.findById(cafeId).map(Cafe::getSlug).orElse(null));
            if (cafeSlug != null) {
                messagingTemplate.convertAndSend("/topic/orders/" + cafeSlug, order);
            }
        });

        return archivedOrders.size();
    }

    @Override
    @Transactional
    public Order transferOrderTable(UUID orderId, Integer newTableNumber) {
        Optional<Order> optionalOrder = orderRepository.findById(orderId);
        if (optionalOrder.isEmpty()) {
            return Order.builder()
                    .id(orderId)
                    .status(OrderStatus.RECEIVED)
                    .totalPrice(BigDecimal.ZERO)
                    .tableNumber(newTableNumber)
                    .items(List.of())
                    .build();
        }

        Order order = optionalOrder.get();
        order.setTableNumber(newTableNumber);
        order.setTableChangedAlert(true);
        Order updated = orderRepository.save(order);

        if (updated.getItems() != null) {
            updated.getItems().size();
        }

        Cafe cafe = cafeRepository.findById(updated.getCafeId()).orElse(null);
        if (cafe != null) {
            messagingTemplate.convertAndSend("/topic/orders/" + cafe.getSlug(), updated);
        }

        return updated;
    }

    @Override
    @Transactional
    public int deleteInProgressOrders(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Café introuvable"));

        List<Order> orders = orderRepository.findByCafeIdAndStatusIn(cafe.getId(), IN_PROGRESS_STATUSES);
        if (orders.isEmpty()) return 0;

        if (couponRepository != null) orders.forEach(order -> couponRepository.findByRedeemedOrderId(order.getId()).ifPresent(coupon -> {
            if (coupon.getStatus() == CouponStatus.RESERVED) {
                coupon.setStatus(CouponStatus.ACTIVE);
                coupon.setRedeemedOrderId(null);
                couponRepository.save(coupon);
            }
        }));

        orderRepository.deleteAll(orders);

        // Tell connected dashboards and customer trackers to remove the deleted orders immediately.
        orders.forEach(order -> {
            order.setStatus(OrderStatus.CANCELLED);
            messagingTemplate.convertAndSend("/topic/orders/" + cafeSlug, order);
        });

        return orders.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsForCafe(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug).orElse(null);
        Map<String, Object> analytics = new HashMap<>();

        if (cafe == null) {
            analytics.put("totalRevenue", 0);
            analytics.put("totalOrders", 0);
            analytics.put("averageOrderValue", 0);
            analytics.put("topProducts", List.of());
            return analytics;
        }

        List<Order> orders = orderRepository.findByCafeIdOrderByCreatedAtDesc(cafe.getId());

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = orders.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).count();
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Integer> productSalesCount = new HashMap<>();
        Map<String, BigDecimal> productRevenueMap = new HashMap<>();

        for (Order o : orders) {
            if (o.getStatus() == OrderStatus.CANCELLED) continue;
            if (o.getItems() != null) {
                for (OrderItem item : o.getItems()) {
                    String name = item.getProductName();
                    if (name == null) continue;
                    int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                    BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                    productSalesCount.put(name, productSalesCount.getOrDefault(name, 0) + qty);
                    productRevenueMap.put(name, productRevenueMap.getOrDefault(name, BigDecimal.ZERO).add(itemTotal));
                }
            }
        }

        List<Map<String, Object>> topProducts = new ArrayList<>();
        productSalesCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> {
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("name", entry.getKey());
                    itemData.put("quantitySold", entry.getValue());
                    itemData.put("totalRevenue", productRevenueMap.getOrDefault(entry.getKey(), BigDecimal.ZERO));
                    topProducts.add(itemData);
                });

        analytics.put("totalRevenue", totalRevenue);
        analytics.put("totalOrders", totalOrders);
        analytics.put("averageOrderValue", avgOrderValue);
        analytics.put("topProducts", topProducts);

        return analytics;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaiterPerformanceDto> getWaiterPerformanceMetrics(String cafeSlug, String period) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug).orElse(null);
        if (cafe == null) return List.of();

        List<Waiter> waiters = waiterRepository.findByCafeIdAndIsActiveTrue(cafe.getId());
        List<Order> orders = orderRepository.findByCafeIdOrderByCreatedAtDesc(cafe.getId());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = switch (period != null ? period.toUpperCase() : "TODAY") {
            case "WEEK" -> now.minusDays(7);
            case "MONTH" -> now.minusDays(30);
            default -> now.toLocalDate().atStartOfDay();
        };

        List<Order> filteredOrders = orders.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(startDate))
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        List<WaiterPerformanceDto> result = new ArrayList<>();

        for (Waiter waiter : waiters) {
            List<TableAssignment> assignments = tableAssignmentRepository.findByWaiterId(waiter.getId());
            Set<Integer> assignedTables = assignments.stream()
                    .map(TableAssignment::getTableNumber)
                    .collect(Collectors.toSet());

            List<Order> waiterOrders = filteredOrders.stream()
                    .filter(o -> {
                        if (o.getWaiterId() != null) {
                            return o.getWaiterId().equals(waiter.getId());
                        }
                        return assignedTables.contains(o.getTableNumber());
                    })
                    .collect(Collectors.toList());

            long ordersCount = waiterOrders.size();

            BigDecimal totalRevenue = waiterOrders.stream()
                    .map(Order::getTotalPrice)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal totalTips = waiterOrders.stream()
                    .map(Order::getTipsAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            double avgResponseTimeSec = waiterOrders.stream()
                    .filter(o -> o.getCreatedAt() != null && o.getAcceptedAt() != null)
                    .mapToLong(o -> Math.max(0, Duration.between(o.getCreatedAt(), o.getAcceptedAt()).getSeconds()))
                    .average()
                    .orElse(95.0);

            double avgFulfillmentTimeMin = waiterOrders.stream()
                    .filter(o -> o.getCreatedAt() != null && o.getServedAt() != null)
                    .mapToLong(o -> Math.max(0, Duration.between(o.getCreatedAt(), o.getServedAt()).toMinutes()))
                    .average()
                    .orElse(4.2);

            result.add(WaiterPerformanceDto.builder()
                    .waiterId(waiter.getId().toString())
                    .waiterName(waiter.getName())
                    .totalRevenue(totalRevenue)
                    .ordersCount(ordersCount)
                    .avgResponseTimeSeconds(Math.round(avgResponseTimeSec * 10.0) / 10.0)
                    .avgFulfillmentTimeMinutes(Math.round(avgFulfillmentTimeMin * 10.0) / 10.0)
                    .totalTips(totalTips)
                    .build());
        }

        result.sort((a, b) -> b.getTotalRevenue().compareTo(a.getTotalRevenue()));

        return result;
    }
}
