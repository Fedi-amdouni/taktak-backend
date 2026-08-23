package com.taktak.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderPayload {
    private String cafeSlug;
    private Integer tableNumber;
    private BigDecimal totalPrice;
    private String couponCode;
    private Double clientLatitude;
    private Double clientLongitude;
    private String participantId;
    private String clientOrderId;
    private List<OrderItemPayload> items;

    @Data
    public static class OrderItemPayload {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private java.util.Map<String, String> selectedOptions;
        private String notes;
    }
}
