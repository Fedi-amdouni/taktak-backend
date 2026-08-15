package com.taktak.controller;

import com.taktak.model.Cafe;
import com.taktak.model.CafeTable;
import com.taktak.service.ICafeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cafes")
@RequiredArgsConstructor
public class CafeController {

    private final ICafeService cafeService;

    @GetMapping
    public ResponseEntity<List<Cafe>> getAllCafes() {
        return ResponseEntity.ok(cafeService.getAllCafes());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Cafe> getCafeBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(cafeService.getCafeBySlug(slug));
    }

    @GetMapping("/{slug}/menu")
    public ResponseEntity<Map<String, Object>> getMenuBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(cafeService.getMenuBySlug(slug));
    }

    @GetMapping("/{slug}/tables")
    public ResponseEntity<List<CafeTable>> getTablesByCafe(@PathVariable String slug) {
        return ResponseEntity.ok(cafeService.getTablesByCafe(slug));
    }

    @PostMapping("/{slug}/tables/batch")
    public ResponseEntity<List<CafeTable>> saveTablesBatch(@PathVariable String slug, @RequestBody List<CafeTable> tables) {
        try {
            return ResponseEntity.ok(cafeService.saveTablesBatch(slug, tables));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(cafeService.uploadImage(file));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Échec de l'upload de l'image"));
        }
    }

    @GetMapping("/{slug}/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@PathVariable String slug) {
        return ResponseEntity.ok(cafeService.getAnalytics(slug));
    }

    @PutMapping("/{slug}/location")
    public ResponseEntity<Cafe> updateLocationSettings(
            @PathVariable String slug,
            @RequestBody Map<String, Double> payload
    ) {
        Double lat = payload.get("latitude");
        Double lng = payload.get("longitude");
        Double radius = payload.get("geofenceRadiusMeters");
        return ResponseEntity.ok(cafeService.updateLocationSettings(slug, lat, lng, radius));
    }

    @GetMapping("/{slug}/tables/{tableNumber}/status")
    public ResponseEntity<Map<String, Object>> getTableStatus(
            @PathVariable String slug,
            @PathVariable Integer tableNumber,
            @RequestParam(value = "token", required = false) String sessionToken
    ) {
        return ResponseEntity.ok(cafeService.getTableStatus(slug, tableNumber, sessionToken));
    }

    @PutMapping("/{slug}/tables/{tableNumber}/toggle-games")
    public ResponseEntity<CafeTable> toggleTableGames(
            @PathVariable String slug,
            @PathVariable Integer tableNumber,
            @RequestBody Map<String, Boolean> payload
    ) {
        Boolean enabled = payload.get("enabled");
        return ResponseEntity.ok(cafeService.toggleTableGames(slug, tableNumber, enabled));
    }

    @GetMapping("/{slug}/check-wifi")
    public ResponseEntity<Map<String, Boolean>> checkWifiStatus(@PathVariable String slug, jakarta.servlet.http.HttpServletRequest request) {
        String clientIp = extractClientIp(request);
        Cafe cafe = cafeService.getCafeBySlug(slug);
        boolean isWifi = false;
        if (cafe != null && cafe.getLastKnownWifiIp() != null && !cafe.getLastKnownWifiIp().isBlank()) {
            isWifi = clientIp.equals(cafe.getLastKnownWifiIp());
        }
        return ResponseEntity.ok(Map.of("onCafeWifi", isWifi));
    }

    private String extractClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
