package com.taktak.controller;

import com.taktak.model.Cafe;
import com.taktak.model.Product;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;
    private final CafeRepository cafeRepository;

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        if (product.getIsAvailable() == null) product.setIsAvailable(true);

        // Fallback cafeId if not provided in payload
        if (product.getCafeId() == null) {
            Cafe defaultCafe = cafeRepository.findBySlug("monastir-lounge")
                    .orElseGet(() -> cafeRepository.save(
                            Cafe.builder()
                                    .name("Monastir Lounge")
                                    .slug("monastir-lounge")
                                    .build()
                    ));
            product.setCafeId(defaultCafe.getId());
        }

        Product saved = productRepository.save(product);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable UUID id, @RequestBody Product product) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(product.getName());
            existing.setPrice(product.getPrice());
            existing.setCategoryId(product.getCategoryId());
            if (product.getImageUrl() != null) {
                existing.setImageUrl(product.getImageUrl());
            }
            if (product.getIsAvailable() != null) {
                existing.setIsAvailable(product.getIsAvailable());
            }
            if (product.getOptionsJson() != null) {
                existing.setOptionsJson(product.getOptionsJson());
            }
            Product updated = productRepository.save(existing);
            return ResponseEntity.ok(updated);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/toggle-availability")
    public ResponseEntity<Product> toggleAvailability(@PathVariable UUID id) {
        return productRepository.findById(id).map(p -> {
            p.setIsAvailable(!p.getIsAvailable());
            Product updated = productRepository.save(p);
            return ResponseEntity.ok(updated);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
