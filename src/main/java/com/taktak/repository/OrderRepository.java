package com.taktak.repository;

import com.taktak.model.Order;
import com.taktak.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCafeIdOrderByCreatedAtDesc(UUID cafeId);
    List<Order> findByCafeIdAndStatusOrderByCreatedAtDesc(UUID cafeId, OrderStatus status);
}
