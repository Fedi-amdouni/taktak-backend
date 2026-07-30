package com.taktak.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmbianceStateDto {

    private PollDto activePoll;
    private List<MusicOptionDto> musicOptions;
    private String userVotedPollOptionId;
    private String userVotedMusicOptionId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PollDto {
        private String id;
        private String title;
        private Integer totalVotes;
        private List<PollOptionDto> options;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PollOptionDto {
        private String id;
        private String optionText;
        private Integer votesCount;
        private Integer percentage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MusicOptionDto {
        private String id;
        private String title;
        private String genre;
        private Integer votesCount;
    }
}
