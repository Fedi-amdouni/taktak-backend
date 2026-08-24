package com.taktak.service.impl;

import com.taktak.dto.CreateOrderPayload;
import com.taktak.dto.WaiterPerformanceDto;
import com.taktak.dto.TableTransferDto;
import com.taktak.model.*;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.CafeTableRepository;
import com.taktak.repository.OrderRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.repository.CouponRepository;
import com.taktak.repository.TableAssignmentRepository;
import com.taktak.repository.WaiterRepository;
import com.taktak.service.IOrderService;
import com.taktak.service.RewardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
@Slf4j
public class OrderServiceImpl implements IOrderService {

    private static final Set<OrderStatus> IN_PROGRESS_STATUSES = EnumSet.of(
            OrderStatus.RECEIVED,
            OrderStatus.PREPARING,
            OrderStatus.READY,
            OrderStatus.PICKED_UP,
            OrderStatus.SERVED
    );

    private static final Set<OrderStatus> TERMINAL_STATUSES = EnumSet.of(
            OrderStatus.ARCHIVED,
            OrderStatus.CANCELLED
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
    private final ProductRepository productRepository;
    private final CafeRepository cafeRepository;
    private final CafeTableRepository cafeTableRepository;
    private final WaiterRepository waiterRepository;
    private final TableAssignmentRepository tableAssignmentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false) private RewardService rewardService;
    @Autowired(required = false) private CouponRepository couponRepository;

    @Value("${taktak.orders.auto-archive-seconds:300}")
    private long autoArchiveSeconds = 300;

    @Value("${taktak.orders.stale-active-hours:24}")
    private long staleActiveHours = 24;

    @Value("${taktak.location.max-gps-accuracy-meters:50}")
    private double maxGpsAccuracyMeters = 50;

    @Override
    @Transactional
    public Order createOrder(CreateOrderPayload payload) {
        return createOrder(payload, null);
    }

    @Override
    @Transactional
    public void updateCafeWifiIp(String cafeSlug, String staffIp) {
        if (cafeSlug == null || staffIp == null || staffIp.isBlank()) {
            return;
        }
        try {
            cafeRepository.findBySlug(cafeSlug).ifPresent(cafe -> {
                if (!staffIp.equals(cafe.getLastKnownWifiIp())) {
                    cafe.setLastKnownWifiIp(staffIp);
                    cafeRepository.save(cafe);
                }
            });
        } catch (Exception ignored) {
            // Non-bloquant
        }
    }

    @Override
    @Transactional
    public Order createOrder(CreateOrderPayload payload, String clientIp) {
        if (payload == null || payload.getCafeSlug() == null || payload.getCafeSlug().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le café est obligatoire");
        }

        String clientOrderId = normalizeClientIdentifier(payload.getClientOrderId(), "clientOrderId");
        String participantId = normalizeClientIdentifier(payload.getParticipantId(), "participantId");

        Cafe cafe = cafeRepository.findBySlug(payload.getCafeSlug())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Café introuvable"));

        if (!Boolean.TRUE.equals(cafe.getOrderingEnabled())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La commande en ligne est dÃ©sactivÃ©e pour ce cafÃ©");
        }

        if (clientOrderId != null) {
            Optional<Order> existingOrder = orderRepository.findByCafeIdAndClientOrderId(cafe.getId(), clientOrderId);
            if (existingOrder.isPresent()) {
                Order existing = existingOrder.get();
                boolean sameTable = Objects.equals(existing.getTableNumber(), payload.getTableNumber());
                boolean sameParticipant = Objects.equals(existing.getParticipantId(), participantId);
                if (!sameTable || !sameParticipant) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Cette référence de commande est déjà utilisée"
                    );
                }
                return existing;
            }
        }

        // Présence & Géolocalisation non-bloquante
        OrderPresenceStatus presenceStatus = OrderPresenceStatus.UNVERIFIED_LOCATION;
        Double distanceMeters = null;

        // 1. Test WiFi : si l'IP client correspond à l'IP WiFi active du café
        if (clientIp != null && cafe.getLastKnownWifiIp() != null
                && !cafe.getLastKnownWifiIp().isBlank()
                && clientIp.equals(cafe.getLastKnownWifiIp())) {
            presenceStatus = OrderPresenceStatus.VERIFIED_WIFI;
        }

        // 2. Test GPS : si coordonnées fournies par le client
        if (payload.getClientLatitude() != null && payload.getClientLongitude() != null
                && cafe.getLatitude() != null && cafe.getLongitude() != null) {
            distanceMeters = calculateHaversineDistance(
                    payload.getClientLatitude(), payload.getClientLongitude(),
                    cafe.getLatitude(), cafe.getLongitude()
            );

            double maxRadius = cafe.getGeofenceRadiusMeters() != null ? cafe.getGeofenceRadiusMeters() : 120.0;
            Double accuracyMeters = payload.getClientAccuracyMeters();
            boolean accurateEnough = accuracyMeters != null
                    && Double.isFinite(accuracyMeters)
                    && accuracyMeters >= 0
                    && accuracyMeters <= maxGpsAccuracyMeters;
            if (accurateEnough && distanceMeters + accuracyMeters <= maxRadius) {
                presenceStatus = OrderPresenceStatus.VERIFIED_GPS;
            }
        }

        BigDecimal subtotal = payload.getTotalPrice() != null ? payload.getTotalPrice() : BigDecimal.ZERO;
        Coupon coupon = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (payload.getCouponCode() != null && !payload.getCouponCode().isBlank()) {
            coupon = rewardService.requireUsable(cafe.getId(), payload.getCouponCode(), subtotal);
            discount = subtotal.multiply(coupon.getDiscountPercent()).divide(new BigDecimal("100"), 3, RoundingMode.HALF_UP);
        }

        Set<UUID> productIds = payload.getItems().stream()
                .map(CreateOrderPayload.OrderItemPayload::getProductId)
                .filter(Objects::nonNull)
                .map(id -> {
                    try {
                        return UUID.fromString(id);
                    } catch (IllegalArgumentException ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int longestPrepMinutes = productRepository.findAllById(productIds).stream()
                .map(Product::getPrepTimeMinutes)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(7);
        int activeQueueSize = orderRepository.findByCafeIdAndStatusIn(cafe.getId(), IN_PROGRESS_STATUSES).size();
        int estimatedWaitMinutes = Math.min(40, Math.max(5, longestPrepMinutes + Math.min(12, activeQueueSize * 2)));
        LocalDateTime estimatedReadyAt = LocalDateTime.now().plusMinutes(estimatedWaitMinutes);

        Order order = Order.builder()
                .cafeId(cafe.getId())
                .tableId(UUID.randomUUID())
                .tableNumber(payload.getTableNumber())
                .participantId(participantId)
                .clientOrderId(clientOrderId)
                .status(OrderStatus.RECEIVED)
                .presenceStatus(presenceStatus)
                .clientLatitude(payload.getClientLatitude())
                .clientLongitude(payload.getClientLongitude())
                .clientAccuracyMeters(payload.getClientAccuracyMeters())
                .distanceMeters(distanceMeters != null ? Math.round(distanceMeters * 10.0) / 10.0 : null)
                .estimatedWaitMinutes(estimatedWaitMinutes)
                .estimatedReadyAt(estimatedReadyAt)
                .clientIp(clientIp)
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

        // La contrainte unique en base empêche deux insertions concurrentes avec la même clé.
        // Si la réponse réseau se perd, le client réessaie avec cette même clé et récupère
        // la commande existante via le contrôle placé au début de la méthode.
        Order saved = orderRepository.saveAndFlush(order);

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
        Order order = orderRepository.findByIdForUpdate(orderId)
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

        if (newStatus == OrderStatus.ARCHIVED) {
            markArchived(order, "STAFF_ARCHIVED", now);
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

        if (newStatus.isTerminal()) {
            releaseTableIfAllOrdersAreTerminal(updated);
        }

        if (updated.getItems() != null) {
            updated.getItems().size();
        }

        Cafe cafe = cafeRepository.findById(updated.getCafeId()).orElse(null);
        if (cafe != null) {
            messagingTemplate.convertAndSend("/topic/orders/" + cafe.getSlug(), updated);
        }

        return updated;
    }

    @Transactional
    public void archivePaidOrders() {
        archivePaidOrdersBefore(LocalDateTime.now().minusSeconds(autoArchiveSeconds));
        archiveStaleActiveOrdersBefore(LocalDateTime.now().minusHours(staleActiveHours));
    }

    public int archivePaidOrdersBefore(LocalDateTime cutoff) {
        List<Order> paidOrders = orderRepository.findByStatusAndUpdatedAtBefore(OrderStatus.PAID, cutoff);
        if (paidOrders.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        paidOrders.forEach(order -> markArchived(order, "PAID_RETENTION_ELAPSED", now));
        List<Order> archivedOrders = orderRepository.saveAll(paidOrders);
        releaseEligibleTables(archivedOrders);

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

    public int archiveStaleActiveOrdersBefore(LocalDateTime cutoff) {
        List<Order> staleOrders = orderRepository.findByStatusInAndUpdatedAtBefore(IN_PROGRESS_STATUSES, cutoff);
        if (staleOrders.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        staleOrders.forEach(order -> markArchived(order, "STALE_ACTIVE_ORDER", now));
        List<Order> archivedOrders = orderRepository.saveAll(staleOrders);
        releaseEligibleTables(archivedOrders);
        broadcastArchivedOrders(archivedOrders);
        archivedOrders.forEach(order -> log.warn(
                "Archived stale order id={} cafeId={} table={} previous inactivity cutoff={}",
                order.getId(), order.getCafeId(), order.getTableNumber(), cutoff
        ));
        return archivedOrders.size();
    }

    @Override
    @Transactional
    public List<Order> transferOrderTable(UUID orderId, String cafeSlug, TableTransferDto request) {
        if (request == null
                || request.getSourceTableNumber() == null
                || request.getSourceTableNumber() <= 0
                || request.getNewTableNumber() == null
                || request.getNewTableNumber() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les tables source et cible sont obligatoires");
        }

        String participantId = normalizeClientIdentifier(request.getParticipantId(), "participantId");
        if (participantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le participant est obligatoire");
        }

        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Café introuvable"));
        Order anchor = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable"));

        if (!Objects.equals(anchor.getCafeId(), cafe.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Commande introuvable");
        }
        if (!Objects.equals(anchor.getParticipantId(), participantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette commande appartient à une autre session");
        }

        CafeTable targetTable = cafeTableRepository
                .findByCafeIdAndTableNumber(cafe.getId().toString(), request.getNewTableNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table cible introuvable"));
        requireMatchingTableToken(targetTable.getSessionToken(), request.getTargetSessionToken());

        if (Objects.equals(request.getSourceTableNumber(), request.getNewTableNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nouvelle table doit être différente");
        }
        if (!IN_PROGRESS_STATUSES.contains(anchor.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette commande ne peut plus être transférée");
        }

        CafeTable sourceTable = cafeTableRepository
                .findByCafeIdAndTableNumber(cafe.getId().toString(), request.getSourceTableNumber())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Table source introuvable"));
        requireMatchingTableToken(sourceTable.getSessionToken(), request.getSourceSessionToken());

        if (Objects.equals(anchor.getTableNumber(), request.getNewTableNumber())) {
            return orderRepository.findByCafeIdAndTableNumberAndParticipantIdAndStatusIn(
                    cafe.getId(), request.getNewTableNumber(), participantId, IN_PROGRESS_STATUSES);
        }
        if (!Objects.equals(anchor.getTableNumber(), request.getSourceTableNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La commande n'est plus sur la table source");
        }

        List<Order> orders = orderRepository.findByCafeIdAndTableNumberAndParticipantIdAndStatusIn(
                cafe.getId(), request.getSourceTableNumber(), participantId, IN_PROGRESS_STATUSES);
        if (orders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucune commande active à transférer");
        }

        orders.forEach(order -> {
            order.setTableNumber(request.getNewTableNumber());
            order.setTableChangedAlert(true);
        });
        List<Order> transferred = orderRepository.saveAll(orders);
        transferred.forEach(order -> messagingTemplate.convertAndSend("/topic/orders/" + cafeSlug, order));
        return transferred;
    }

    private void requireMatchingTableToken(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()
                || !java.security.MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                provided.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Jeton de table invalide");
        }
    }

    @Override
    @Transactional
    public int archiveInProgressOrders(String cafeSlug) {
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

        LocalDateTime now = LocalDateTime.now();
        orders.forEach(order -> markArchived(order, "STAFF_BULK_ARCHIVE", now));
        List<Order> archivedOrders = orderRepository.saveAll(orders);
        releaseEligibleTables(archivedOrders);
        archivedOrders.forEach(order -> messagingTemplate.convertAndSend("/topic/orders/" + cafeSlug, order));

        log.info("Staff bulk-archived {} active orders for cafe={}", archivedOrders.size(), cafeSlug);

        return archivedOrders.size();
    }

    private void markArchived(Order order, String reason, LocalDateTime archivedAt) {
        order.setStatus(OrderStatus.ARCHIVED);
        order.setArchivedAt(archivedAt);
        order.setArchiveReason(reason);
    }

    private void releaseEligibleTables(Collection<Order> orders) {
        Set<String> checkedTables = new HashSet<>();
        for (Order order : orders) {
            String key = order.getCafeId() + ":" + order.getTableNumber();
            if (checkedTables.add(key)) {
                releaseTableIfAllOrdersAreTerminal(order);
            }
        }
    }

    private void releaseTableIfAllOrdersAreTerminal(Order order) {
        if (order.getCafeId() == null || order.getTableNumber() == null) return;
        boolean hasNonTerminalOrders = orderRepository.existsByCafeIdAndTableNumberAndStatusNotIn(
                order.getCafeId(),
                order.getTableNumber(),
                TERMINAL_STATUSES
        );
        if (hasNonTerminalOrders) return;

        cafeTableRepository.findByCafeIdAndTableNumber(order.getCafeId().toString(), order.getTableNumber())
                .ifPresent(table -> {
                    table.setSessionToken(UUID.randomUUID().toString());
                    cafeTableRepository.save(table);
                    log.info("Released table cafeId={} table={} after all orders became terminal",
                            order.getCafeId(), order.getTableNumber());
                });
    }

    private void broadcastArchivedOrders(Collection<Order> archivedOrders) {
        Map<UUID, String> cafeSlugs = new HashMap<>();
        archivedOrders.forEach(order -> {
            String cafeSlug = cafeSlugs.computeIfAbsent(order.getCafeId(), cafeId ->
                    cafeRepository.findById(cafeId).map(Cafe::getSlug).orElse(null));
            if (cafeSlug != null) {
                messagingTemplate.convertAndSend("/topic/orders/" + cafeSlug, order);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getAnalyticsForCafe(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug).orElse(null);
        Map<String, Object> analytics = new HashMap<>();

        if (cafe == null) {
            analytics.put("totalRevenue", BigDecimal.ZERO);
            analytics.put("totalOrders", 0L);
            analytics.put("averageOrderValue", BigDecimal.ZERO);
            analytics.put("topProducts", List.of());
            analytics.put("hourlyDistribution", List.of());
            analytics.put("statusBreakdown", Map.of());
            analytics.put("cancellationRate", 0.0);
            analytics.put("averageFulfillmentTimeMinutes", 0.0);
            analytics.put("totalTips", BigDecimal.ZERO);
            return analytics;
        }

        List<Order> allOrders = orderRepository.findByCafeIdOrderByCreatedAtDesc(cafe.getId());
        List<Order> validOrders = allOrders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .collect(Collectors.toList());

        BigDecimal totalRevenue = validOrders.stream()
                .map(Order::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalTips = validOrders.stream()
                .map(Order::getTipsAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long totalOrders = validOrders.size();
        long totalAllOrders = allOrders.size();
        long cancelledOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        double cancellationRate = totalAllOrders > 0
                ? Math.round(((double) cancelledOrders / totalAllOrders * 100.0) * 10.0) / 10.0
                : 0.0;

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 3, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Top products by revenue and quantity
        Map<String, Integer> productSalesCount = new HashMap<>();
        Map<String, BigDecimal> productRevenueMap = new HashMap<>();

        for (Order o : validOrders) {
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
                .limit(6)
                .forEach(entry -> {
                    Map<String, Object> itemData = new HashMap<>();
                    itemData.put("name", entry.getKey());
                    itemData.put("quantitySold", entry.getValue());
                    itemData.put("totalRevenue", productRevenueMap.getOrDefault(entry.getKey(), BigDecimal.ZERO));
                    topProducts.add(itemData);
                });

        // Hourly peak distribution (08:00 to 23:00)
        Map<Integer, Long> hourOrderCounts = new TreeMap<>();
        Map<Integer, BigDecimal> hourRevenueMap = new TreeMap<>();
        for (int h = 8; h <= 23; h++) {
            hourOrderCounts.put(h, 0L);
            hourRevenueMap.put(h, BigDecimal.ZERO);
        }

        for (Order o : validOrders) {
            if (o.getCreatedAt() != null) {
                int h = o.getCreatedAt().getHour();
                if (h >= 8 && h <= 23) {
                    hourOrderCounts.put(h, hourOrderCounts.getOrDefault(h, 0L) + 1);
                    BigDecimal price = o.getTotalPrice() != null ? o.getTotalPrice() : BigDecimal.ZERO;
                    hourRevenueMap.put(h, hourRevenueMap.getOrDefault(h, BigDecimal.ZERO).add(price));
                }
            }
        }

        List<Map<String, Object>> hourlyDistribution = new ArrayList<>();
        hourOrderCounts.forEach((hour, count) -> {
            Map<String, Object> hData = new HashMap<>();
            hData.put("hour", String.format("%02dh", hour));
            hData.put("ordersCount", count);
            hData.put("revenue", hourRevenueMap.getOrDefault(hour, BigDecimal.ZERO));
            hourlyDistribution.add(hData);
        });

        // Status breakdown
        Map<String, Long> statusBreakdown = new HashMap<>();
        for (OrderStatus st : OrderStatus.values()) {
            statusBreakdown.put(st.name(), allOrders.stream().filter(o -> o.getStatus() == st).count());
        }

        // Average fulfillment time
        double avgFulfillmentTimeMin = validOrders.stream()
                .filter(o -> o.getCreatedAt() != null && o.getServedAt() != null)
                .mapToLong(o -> Math.max(0, Duration.between(o.getCreatedAt(), o.getServedAt()).toMinutes()))
                .average()
                .orElse(5.5);

        analytics.put("totalRevenue", totalRevenue);
        analytics.put("totalOrders", totalOrders);
        analytics.put("averageOrderValue", avgOrderValue);
        analytics.put("totalTips", totalTips);
        analytics.put("cancellationRate", cancellationRate);
        analytics.put("averageFulfillmentTimeMinutes", Math.round(avgFulfillmentTimeMin * 10.0) / 10.0);
        analytics.put("topProducts", topProducts);
        analytics.put("hourlyDistribution", hourlyDistribution);
        analytics.put("statusBreakdown", statusBreakdown);

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

        // Map each table to its primary assigned waiter to avoid double counting
        Map<Integer, UUID> primaryWaiterByTable = new HashMap<>();
        for (Waiter waiter : waiters) {
            List<TableAssignment> assignments = tableAssignmentRepository.findByWaiterId(waiter.getId());
            for (TableAssignment a : assignments) {
                primaryWaiterByTable.putIfAbsent(a.getTableNumber(), waiter.getId());
            }
        }

        List<WaiterPerformanceDto> result = new ArrayList<>();

        for (Waiter waiter : waiters) {
            List<Order> waiterOrders = filteredOrders.stream()
                    .filter(o -> {
                        if (o.getWaiterId() != null) {
                            return o.getWaiterId().equals(waiter.getId());
                        }
                        // If no direct waiterId on order, attribute uniquely to primary assigned waiter
                        UUID primary = primaryWaiterByTable.get(o.getTableNumber());
                        return primary != null && primary.equals(waiter.getId());
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

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Rayon de la Terre en mètres
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String normalizeClientIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " est trop long");
        }
        return normalized;
    }
}
