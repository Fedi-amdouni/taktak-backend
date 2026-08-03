package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cafe_id", nullable = false)
    private UUID cafeId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @Column(name = "promo_price", precision = 19, scale = 6)
    private BigDecimal promoPrice;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_combo")
    @Builder.Default
    private Boolean isCombo = false;

    @Column(name = "combo_slots_json", columnDefinition = "text")
    private String comboSlotsJson;

    @Column(name = "prep_time_minutes")
    private Integer prepTimeMinutes;

    @Column(name = "badge")
    private String badge;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "product_cross_sells", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "suggested_product_id")
    @Builder.Default
    private List<UUID> suggestedProductIds = new ArrayList<>();
}
