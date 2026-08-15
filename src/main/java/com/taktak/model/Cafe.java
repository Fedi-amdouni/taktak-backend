package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cafes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cafe {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "last_known_wifi_ip")
    private String lastKnownWifiIp;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "geofence_radius_meters")
    @Builder.Default
    private Double geofenceRadiusMeters = 120.0;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.geofenceRadiusMeters == null) {
            this.geofenceRadiusMeters = 120.0;
        }
    }
}
