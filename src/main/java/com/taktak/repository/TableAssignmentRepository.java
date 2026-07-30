package com.taktak.repository;

import com.taktak.model.TableAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TableAssignmentRepository extends JpaRepository<TableAssignment, UUID> {
    List<TableAssignment> findByWaiterId(UUID waiterId);
    List<TableAssignment> findByCafeId(UUID cafeId);
    void deleteByWaiterId(UUID waiterId);
}
