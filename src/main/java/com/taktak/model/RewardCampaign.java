package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "reward_campaigns") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardCampaign {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name="cafe_id", nullable=false, unique=true) private UUID cafeId;
    @Builder.Default private boolean enabled = false;
    @Column(name="google_review_url") private String googleReviewUrl;
    @Column(name="coupon_valid_days") @Builder.Default private Integer couponValidDays = 30;
    @Column(name="participation_cooldown_days") @Builder.Default private Integer participationCooldownDays = 30;
    @Column(name="minimum_order_amount", precision=10, scale=3) @Builder.Default private BigDecimal minimumOrderAmount = BigDecimal.ZERO;
    @Column(name="created_at") private LocalDateTime createdAt;
    @Column(name="updated_at") private LocalDateTime updatedAt;
    @PrePersist void create(){createdAt=updatedAt=LocalDateTime.now();normalize();}
    @PostLoad void normalize(){if(couponValidDays==null)couponValidDays=30;if(participationCooldownDays==null)participationCooldownDays=30;if(minimumOrderAmount==null)minimumOrderAmount=BigDecimal.ZERO;}
    @PreUpdate void update(){updatedAt=LocalDateTime.now();}
}
