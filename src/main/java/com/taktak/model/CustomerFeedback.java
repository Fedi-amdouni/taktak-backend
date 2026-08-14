package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="customer_feedback", uniqueConstraints=@UniqueConstraint(columnNames="order_id")) @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerFeedback {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="cafe_id", nullable=false) private UUID cafeId;
    @Column(name="order_id", nullable=false) private UUID orderId;
    @Column(nullable=false) private Integer rating;
    @Column(length=1000) private String comment;
    @Column(name="customer_email", nullable=false) private String customerEmail;
    @Column(name="created_at") private LocalDateTime createdAt;
    @PrePersist void create(){createdAt=LocalDateTime.now();}
}
