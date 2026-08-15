package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name="reward_options") @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RewardOption {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="campaign_id", nullable=false) private UUID campaignId;
    @Column(nullable=false) private String label;
    @Column(name="discount_percent", nullable=false, precision=5, scale=2) private BigDecimal discountPercent;
    @Column(name="probability_percent", nullable=false, precision=5, scale=2) private BigDecimal probabilityPercent;
    @Builder.Default private boolean enabled = true;
}
