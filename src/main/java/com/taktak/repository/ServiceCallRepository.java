package com.taktak.repository;

import com.taktak.model.ServiceCall;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ServiceCallRepository extends JpaRepository<ServiceCall, UUID> {
    List<ServiceCall> findByCafeIdAndActiveTrueOrderByCreatedAtDesc(UUID cafeId);
}
