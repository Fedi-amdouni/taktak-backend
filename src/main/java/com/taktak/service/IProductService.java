package com.taktak.service;

import com.taktak.model.Product;

import java.util.Optional;
import java.util.UUID;

public interface IProductService {
    Product createProduct(Product product);
    Optional<Product> updateProduct(UUID id, Product product);
    Optional<Product> toggleAvailability(UUID id);
}
