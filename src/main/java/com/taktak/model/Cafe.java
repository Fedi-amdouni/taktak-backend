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

    @Column(name = "ordering_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean orderingEnabled = true;

    @Column(name = "waiter_calls_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean waiterCallsEnabled = true;

    @Column(name = "games_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean gamesEnabled = true;

    @Column(name = "ambiance_voting_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean ambianceVotingEnabled = true;

    @Column(name = "rewards_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean rewardsEnabled = true;

    @Column(name = "tv_menu_enabled", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    @Builder.Default
    private Boolean tvMenuEnabled = true;

    @Column(name = "tv_menu_style", nullable = false, columnDefinition = "VARCHAR(32) DEFAULT 'ELEGANT'")
    @Builder.Default
    private String tvMenuStyle = "ELEGANT";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.geofenceRadiusMeters == null) {
            this.geofenceRadiusMeters = 120.0;
        }
        if (this.orderingEnabled == null) this.orderingEnabled = true;
        if (this.waiterCallsEnabled == null) this.waiterCallsEnabled = true;
        if (this.gamesEnabled == null) this.gamesEnabled = true;
        if (this.ambianceVotingEnabled == null) this.ambianceVotingEnabled = true;
        if (this.rewardsEnabled == null) this.rewardsEnabled = true;
        if (this.tvMenuEnabled == null) this.tvMenuEnabled = true;
        if (this.tvMenuStyle == null || this.tvMenuStyle.isBlank()) this.tvMenuStyle = "ELEGANT";
    }
}
