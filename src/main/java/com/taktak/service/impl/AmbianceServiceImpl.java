package com.taktak.service.impl;

import com.taktak.dto.AmbianceStateDto;
import com.taktak.model.*;
import com.taktak.repository.*;
import com.taktak.service.IAmbianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmbianceServiceImpl implements IAmbianceService {

    private final CafeRepository cafeRepository;
    private final EventPollRepository eventPollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final MusicVoteOptionRepository musicVoteOptionRepository;
    private final UserVoteRecordRepository userVoteRecordRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional(readOnly = true)
    public AmbianceStateDto getAmbianceState(String cafeSlug, String voterSessionId) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug).orElse(null);
        if (cafe == null) return AmbianceStateDto.builder().build();

        // 1. Get active match poll
        Optional<EventPoll> activePollOpt = eventPollRepository.findFirstByCafeIdAndIsActiveTrueOrderByCreatedAtDesc(cafe.getId());
        AmbianceStateDto.PollDto pollDto = null;
        String userVotedPollOptId = null;

        if (activePollOpt.isPresent()) {
            EventPoll poll = activePollOpt.get();
            int totalVotes = poll.getOptions().stream().mapToInt(PollOption::getVotesCount).sum();

            List<AmbianceStateDto.PollOptionDto> optionDtos = poll.getOptions().stream().map(opt -> {
                int pct = totalVotes > 0 ? (int) Math.round((opt.getVotesCount() * 100.0) / totalVotes) : 0;
                return AmbianceStateDto.PollOptionDto.builder()
                        .id(opt.getId().toString())
                        .optionText(opt.getOptionText())
                        .votesCount(opt.getVotesCount())
                        .percentage(pct)
                        .build();
            }).toList();

            pollDto = AmbianceStateDto.PollDto.builder()
                    .id(poll.getId().toString())
                    .title(poll.getTitle())
                    .totalVotes(totalVotes)
                    .options(optionDtos)
                    .build();

            if (voterSessionId != null && !voterSessionId.isBlank()) {
                Optional<UserVoteRecord> record = userVoteRecordRepository.findByVoterSessionIdAndPollId(voterSessionId, poll.getId());
                if (record.isPresent() && record.get().getPollOptionId() != null) {
                    userVotedPollOptId = record.get().getPollOptionId().toString();
                }
            }
        }

        // 2. Get music vote options
        List<MusicVoteOption> musicOptions = musicVoteOptionRepository.findByCafeIdAndIsActiveTrueOrderByVotesCountDesc(cafe.getId());
        List<AmbianceStateDto.MusicOptionDto> musicDtos = musicOptions.stream().map(m ->
                AmbianceStateDto.MusicOptionDto.builder()
                        .id(m.getId().toString())
                        .title(m.getTitle())
                        .genre(m.getGenre())
                        .votesCount(m.getVotesCount())
                        .build()
        ).toList();

        String userVotedMusicOptId = null;
        if (voterSessionId != null && !voterSessionId.isBlank()) {
            Optional<UserVoteRecord> musicRecord = userVoteRecordRepository.findByVoterSessionIdAndMusicOptionIdIsNotNull(voterSessionId);
            if (musicRecord.isPresent() && musicRecord.get().getMusicOptionId() != null) {
                userVotedMusicOptId = musicRecord.get().getMusicOptionId().toString();
            }
        }

        return AmbianceStateDto.builder()
                .activePoll(pollDto)
                .musicOptions(musicDtos)
                .userVotedPollOptionId(userVotedPollOptId)
                .userVotedMusicOptionId(userVotedMusicOptId)
                .build();
    }

    @Override
    @Transactional
    public AmbianceStateDto votePoll(String cafeSlug, UUID optionId, String voterSessionId) {
        PollOption option = pollOptionRepository.findById(optionId)
                .orElseThrow(() -> new RuntimeException("Option de sondage non trouvée"));

        EventPoll poll = option.getEventPoll();

        // Check if user already voted in this poll
        Optional<UserVoteRecord> existingRecord = userVoteRecordRepository.findByVoterSessionIdAndPollId(voterSessionId, poll.getId());
        if (existingRecord.isEmpty()) {
            option.setVotesCount(option.getVotesCount() + 1);
            pollOptionRepository.save(option);

            userVoteRecordRepository.save(UserVoteRecord.builder()
                    .voterSessionId(voterSessionId)
                    .pollId(poll.getId())
                    .pollOptionId(option.getId())
                    .build());
        }

        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, voterSessionId);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }

    @Override
    @Transactional
    public AmbianceStateDto voteMusic(String cafeSlug, UUID musicOptionId, String voterSessionId) {
        MusicVoteOption musicOption = musicVoteOptionRepository.findById(musicOptionId)
                .orElseThrow(() -> new RuntimeException("Option musique non trouvée"));

        Optional<UserVoteRecord> existingRecord = userVoteRecordRepository.findByVoterSessionIdAndMusicOptionIdIsNotNull(voterSessionId);
        if (existingRecord.isEmpty()) {
            musicOption.setVotesCount(musicOption.getVotesCount() + 1);
            musicVoteOptionRepository.save(musicOption);

            userVoteRecordRepository.save(UserVoteRecord.builder()
                    .voterSessionId(voterSessionId)
                    .musicOptionId(musicOption.getId())
                    .build());
        }

        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, voterSessionId);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }

    @Override
    @Transactional
    public AmbianceStateDto createPoll(String cafeSlug, String title, List<String> optionTexts) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));

        // Deactivate previous active polls
        Optional<EventPoll> activePollOpt = eventPollRepository.findFirstByCafeIdAndIsActiveTrueOrderByCreatedAtDesc(cafe.getId());
        if (activePollOpt.isPresent()) {
            EventPoll oldPoll = activePollOpt.get();
            oldPoll.setIsActive(false);
            eventPollRepository.save(oldPoll);
        }

        EventPoll newPoll = EventPoll.builder()
                .cafeId(cafe.getId())
                .title(title)
                .isActive(true)
                .options(new ArrayList<>())
                .build();

        for (String text : optionTexts) {
            if (text != null && !text.isBlank()) {
                newPoll.addOption(PollOption.builder()
                        .optionText(text.trim())
                        .votesCount(0)
                        .build());
            }
        }

        eventPollRepository.save(newPoll);

        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, null);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }

    @Override
    @Transactional
    public AmbianceStateDto resetMusicVotes(String cafeSlug) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));

        List<MusicVoteOption> musicOptions = musicVoteOptionRepository.findByCafeIdAndIsActiveTrueOrderByVotesCountDesc(cafe.getId());
        for (MusicVoteOption m : musicOptions) {
            m.setVotesCount(0);
        }
        musicVoteOptionRepository.saveAll(musicOptions);
        userVoteRecordRepository.deleteByMusicOptionIdIsNotNull();

        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, null);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }

    @Override
    @Transactional
    public AmbianceStateDto proposeMusic(String cafeSlug, String title, String genre, String voterSessionId) {
        Cafe cafe = cafeRepository.findBySlug(cafeSlug)
                .orElseThrow(() -> new RuntimeException("Café non trouvé"));

        if (title == null || title.isBlank()) {
            throw new RuntimeException("Le titre ou l'artiste est requis");
        }

        MusicVoteOption newMusic = MusicVoteOption.builder()
                .cafeId(cafe.getId())
                .title(title.trim())
                .genre(genre != null && !genre.isBlank() ? genre.trim() : "Demande Client")
                .votesCount(1)
                .isActive(true)
                .build();

        newMusic = musicVoteOptionRepository.save(newMusic);

        if (voterSessionId != null && !voterSessionId.isBlank()) {
            userVoteRecordRepository.save(UserVoteRecord.builder()
                    .voterSessionId(voterSessionId)
                    .musicOptionId(newMusic.getId())
                    .build());
        }

        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, voterSessionId);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }

    @Override
    @Transactional
    public AmbianceStateDto deleteMusicOption(String cafeSlug, UUID musicOptionId) {
        musicVoteOptionRepository.deleteById(musicOptionId);
        AmbianceStateDto updatedState = getAmbianceState(cafeSlug, null);
        messagingTemplate.convertAndSend("/topic/ambiance/" + cafeSlug, updatedState);
        return updatedState;
    }
}
