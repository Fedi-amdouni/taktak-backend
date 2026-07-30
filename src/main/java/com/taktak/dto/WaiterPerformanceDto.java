package com.taktak.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaiterPerformanceDto {
    private String waiterId;
    private String waiterName;
    private BigDecimal totalRevenue;
    private Long ordersCount;
    private Double avgResponseTimeSeconds;
    private Double avgFulfillmentTimeMinutes;
    private BigDecimal totalTips;
}
