package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "floor_plans")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FloorPlan {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    @Column(nullable = false)
    private String cafeId;
    @Column(nullable = false)
    private String name;
    @Builder.Default @Column(nullable = false)
    private Integer width = 12;
    @Builder.Default @Column(nullable = false)
    private Integer height = 8;
    @Builder.Default @Column(nullable = false)
    private Integer sortOrder = 0;
}
