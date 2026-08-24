package com.taktak.repository;

import com.taktak.model.Order;
import com.taktak.model.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Collection;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    List<Order> findByCafeIdOrderByCreatedAtDesc(UUID cafeId);
    List<Order> findByCafeIdAndStatusOrderByCreatedAtDesc(UUID cafeId, OrderStatus status);
    List<Order> findByCafeIdAndStatusIn(UUID cafeId, Collection<OrderStatus> statuses);
    List<Order> findByStatusAndUpdatedAtBefore(OrderStatus status, LocalDateTime cutoff);
    List<Order> findByStatusInAndUpdatedAtBefore(Collection<OrderStatus> statuses, LocalDateTime cutoff);
    boolean existsByCafeIdAndTableNumberAndStatusNotIn(
            UUID cafeId,
            Integer tableNumber,
            Collection<OrderStatus> statuses
    );
    Optional<Order> findByCafeIdAndClientOrderId(UUID cafeId, String clientOrderId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Order> findByCafeIdAndTableNumberAndParticipantIdAndStatusIn(
            UUID cafeId,
            Integer tableNumber,
            String participantId,
            Collection<OrderStatus> statuses
    );
}
