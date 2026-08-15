package com.taktak.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "cafe_tables")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CafeTable {

    public CafeTable(String id, String cafeId, Integer tableNumber, String tableCode, String zoneName,
                     Double posX, Double posY, Double width, Double height,
                     String shape, Integer seatsCount, Integer rotation) {
        this.id = id;
        this.cafeId = cafeId;
        this.tableNumber = tableNumber;
        this.tableCode = tableCode;
        this.zoneName = zoneName;
        this.posX = posX;
        this.posY = posY;
        this.width = width;
        this.height = height;
        this.shape = shape;
        this.seatsCount = seatsCount;
        this.rotation = rotation;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String cafeId;

    @Column(nullable = false)
    private Integer tableNumber;

    private String tableCode; // Ex: "LI1", "T5", "RT2"

    private String zoneName; // Ex: "Terrasse 🌿", "Lounge Intérieur 1 ☕", "Rooftop 🌇"

    @Column(name = "floor_plan_id")
    private String floorPlanId;

    // 2D Floor Plan layout coordinates (relative % 0 to 100)
    private Double posX;
    private Double posY;

    private Double width;
    private Double height;

    private String shape; // "SQUARE", "ROUND", "RECTANGLE", "SOFA"
    private Integer seatsCount;
    private Integer rotation;

    @Column(name = "session_token")
    private String sessionToken; // Token de session rotatif invalidé à l'encaissement

    @Column(name = "games_enabled_override")
    private Boolean gamesEnabledOverride; // null = auto (si commande active), true = forcé ON (ami/VIP), false = forcé OFF
}
