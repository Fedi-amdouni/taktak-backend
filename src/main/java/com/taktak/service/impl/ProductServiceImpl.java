package com.taktak.service.impl;

import com.taktak.model.Cafe;
import com.taktak.model.Product;
import com.taktak.repository.CafeRepository;
import com.taktak.repository.ProductRepository;
import com.taktak.service.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements IProductService {

    private final ProductRepository productRepository;
    private final CafeRepository cafeRepository;

    @Override
    @Transactional
    public Product createProduct(Product product) {
        if (product.getIsAvailable() == null) product.setIsAvailable(true);

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

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public Optional<Product> updateProduct(UUID id, Product product) {
        return productRepository.findById(id).map(existing -> {
            existing.setName(product.getName());
            existing.setPrice(product.getPrice());
            existing.setCategoryId(product.getCategoryId());
            existing.setPromoPrice(product.getPromoPrice());
            existing.setBadge(product.getBadge());
            if (product.getImageUrl() != null) {
                existing.setImageUrl(product.getImageUrl());
            }
            if (product.getIsAvailable() != null) {
                existing.setIsAvailable(product.getIsAvailable());
            }
            if (product.getOptionsJson() != null) {
                existing.setOptionsJson(product.getOptionsJson());
            }
            existing.setDescription(product.getDescription());
            existing.setIsCombo(product.getIsCombo());
            existing.setComboSlotsJson(product.getComboSlotsJson());
            existing.setPrepTimeMinutes(product.getPrepTimeMinutes());
            return productRepository.save(existing);
        });
    }

    @Override
    @Transactional
    public Optional<Product> toggleAvailability(UUID id) {
        return productRepository.findById(id).map(p -> {
            p.setIsAvailable(!p.getIsAvailable());
            return productRepository.save(p);
        });
    }
}
