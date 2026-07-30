package com.taktak.repository;

import com.taktak.model.MusicVoteOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MusicVoteOptionRepository extends JpaRepository<MusicVoteOption, UUID> {
    List<MusicVoteOption> findByCafeIdAndIsActiveTrueOrderByVotesCountDesc(UUID cafeId);
}
