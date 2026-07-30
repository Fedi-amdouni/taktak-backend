package com.taktak.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "user_vote_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVoteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "voter_session_id", nullable = false)
    private String voterSessionId;

    @Column(name = "poll_id")
    private UUID pollId;

    @Column(name = "poll_option_id")
    private UUID pollOptionId;

    @Column(name = "music_option_id")
    private UUID musicOptionId;
}
