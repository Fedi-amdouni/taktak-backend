package com.taktak.repository;

import com.taktak.model.EventPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EventPollRepository extends JpaRepository<EventPoll, UUID> {
    Optional<EventPoll> findFirstByCafeIdAndIsActiveTrueOrderByCreatedAtDesc(UUID cafeId);
}
