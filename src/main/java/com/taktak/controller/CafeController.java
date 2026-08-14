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
}
