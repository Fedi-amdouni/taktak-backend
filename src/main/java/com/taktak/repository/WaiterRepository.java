package com.taktak.repository;

import com.taktak.model.Waiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WaiterRepository extends JpaRepository<Waiter, UUID> {
    List<Waiter> findByCafeIdAndIsActiveTrue(UUID cafeId);
    Optional<Waiter> findByCafeIdAndPinCodeAndIsActiveTrue(UUID cafeId, String pinCode);
    Optional<Waiter> findByCafeIdAndName(UUID cafeId, String name);
}
