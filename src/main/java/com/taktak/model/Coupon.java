package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="coupons") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Coupon {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, unique=true, length=24) private String code;
    @Column(name="cafe_id", nullable=false) private UUID cafeId;
    @Column(name="source_order_id", unique=true) private UUID sourceOrderId;
    @Column(name="redeemed_order_id") private UUID redeemedOrderId;
    @Column(name="customer_email", nullable=false) private String customerEmail;
    @Column(name="reward_label", nullable=false) private String rewardLabel;
    @Column(name="discount_percent", nullable=false, precision=5, scale=2) private BigDecimal discountPercent;
    @Column(name="minimum_order_amount", nullable=false, precision=10, scale=3) private BigDecimal minimumOrderAmount;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private CouponStatus status;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="expires_at", nullable=false) private LocalDateTime expiresAt;
    @Column(name="used_at") private LocalDateTime usedAt;
    @PrePersist void create(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
