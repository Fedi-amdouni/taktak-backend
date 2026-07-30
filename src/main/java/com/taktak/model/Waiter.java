package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "waiters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Waiter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cafe_id", nullable = false)
    private UUID cafeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "pin_code", nullable = false)
    private String pinCode;

    @Column(name = "shift_hours")
    private String shiftHours;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
