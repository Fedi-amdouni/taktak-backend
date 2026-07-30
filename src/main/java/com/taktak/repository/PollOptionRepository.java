package com.taktak.repository;

import com.taktak.model.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PollOptionRepository extends JpaRepository<PollOption, UUID> {
}
