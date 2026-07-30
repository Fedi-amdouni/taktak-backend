package com.taktak.repository;

import com.taktak.model.UserVoteRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserVoteRecordRepository extends JpaRepository<UserVoteRecord, UUID> {
    Optional<UserVoteRecord> findByVoterSessionIdAndPollId(String voterSessionId, UUID pollId);
    Optional<UserVoteRecord> findByVoterSessionIdAndMusicOptionIdIsNotNull(String voterSessionId);
    void deleteByMusicOptionIdIsNotNull();
}
