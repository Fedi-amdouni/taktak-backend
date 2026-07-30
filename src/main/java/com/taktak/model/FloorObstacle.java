package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "floor_obstacles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FloorObstacle {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false)
    private String floorPlanId;
    @Builder.Default @Column(nullable = false)
    private String label = "Mur";
    @Builder.Default @Column(nullable = false)
    private Double posX = 40.0;
    @Builder.Default @Column(nullable = false)
    private Double posY = 40.0;
    @Builder.Default @Column(nullable = false)
    private Double width = 30.0;
    @Builder.Default @Column(nullable = false)
    private Double height = 3.0;
}
