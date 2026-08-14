package com.taktak.service;

import com.taktak.dto.AmbianceStateDto;

import java.util.List;
import java.util.UUID;

public interface IAmbianceService {
    AmbianceStateDto getAmbianceState(String cafeSlug, String voterSessionId);
    AmbianceStateDto votePoll(String cafeSlug, UUID optionId, String voterSessionId);
    AmbianceStateDto voteMusic(String cafeSlug, UUID musicOptionId, String voterSessionId);
    AmbianceStateDto createPoll(String cafeSlug, String title, List<String> optionTexts);
    AmbianceStateDto resetMusicVotes(String cafeSlug);
    AmbianceStateDto proposeMusic(String cafeSlug, String title, String genre, String voterSessionId);
    AmbianceStateDto deleteMusicOption(String cafeSlug, UUID musicOptionId);
}
