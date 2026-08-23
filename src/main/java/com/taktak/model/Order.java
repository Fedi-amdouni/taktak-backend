package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_orders_cafe_client_order_jpa",
                columnNames = {"cafe_id", "client_order_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cafe_id", nullable = false)
    private UUID cafeId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "table_number", nullable = false)
    private Integer tableNumber;

    @Column(name = "participant_id", length = 64)
    private String participantId;

    @Column(name = "client_order_id", length = 64)
    private String clientOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.RECEIVED;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 3)
    private BigDecimal totalPrice;

    @Column(name = "coupon_id")
    private UUID couponId;

    @Column(name = "discount_amount", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tips_amount", precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal tipsAmount = BigDecimal.ZERO;

    @Column(name = "waiter_id")
    private UUID waiterId;

    @Column(name = "table_changed_alert")
    @Builder.Default
    private Boolean tableChangedAlert = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "presence_status")
    @Builder.Default
    private OrderPresenceStatus presenceStatus = OrderPresenceStatus.UNVERIFIED_LOCATION;

    @Column(name = "client_latitude")
    private Double clientLatitude;

    @Column(name = "client_longitude")
    private Double clientLongitude;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "estimated_wait_minutes")
    private Integer estimatedWaitMinutes;

    @Column(name = "estimated_ready_at")
    private LocalDateTime estimatedReadyAt;

    @Column(name = "client_ip")
    private String clientIp;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
        if (this.tipsAmount == null) {
            this.tipsAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
