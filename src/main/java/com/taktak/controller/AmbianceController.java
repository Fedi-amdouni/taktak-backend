package com.taktak.controller;

import com.taktak.dto.AmbianceStateDto;
import com.taktak.service.AmbianceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cafes/{cafeSlug}/ambiance")
@RequiredArgsConstructor
public class AmbianceController {

    private final AmbianceService ambianceService;

    @GetMapping("/active")
    public ResponseEntity<AmbianceStateDto> getActiveAmbiance(
            @PathVariable String cafeSlug,
            @RequestParam(required = false) String voterSessionId
    ) {
        return ResponseEntity.ok(ambianceService.getAmbianceState(cafeSlug, voterSessionId));
    }

    @PostMapping("/vote-poll")
    public ResponseEntity<AmbianceStateDto> votePoll(
            @PathVariable String cafeSlug,
            @RequestBody VoteRequest payload
    ) {
        return ResponseEntity.ok(ambianceService.votePoll(cafeSlug, UUID.fromString(payload.getOptionId()), payload.getVoterSessionId()));
    }

    @PostMapping("/vote-music")
    public ResponseEntity<AmbianceStateDto> voteMusic(
            @PathVariable String cafeSlug,
            @RequestBody VoteRequest payload
    ) {
        return ResponseEntity.ok(ambianceService.voteMusic(cafeSlug, UUID.fromString(payload.getOptionId()), payload.getVoterSessionId()));
    }

    @PostMapping("/polls")
    public ResponseEntity<AmbianceStateDto> createPoll(
            @PathVariable String cafeSlug,
            @RequestBody CreatePollRequest payload
    ) {
        return ResponseEntity.ok(ambianceService.createPoll(cafeSlug, payload.getTitle(), payload.getOptions()));
    }

    @PutMapping("/reset-music")
    public ResponseEntity<AmbianceStateDto> resetMusic(@PathVariable String cafeSlug) {
        return ResponseEntity.ok(ambianceService.resetMusicVotes(cafeSlug));
    }

    @Data
    public static class VoteRequest {
        private String optionId;
        private String voterSessionId;
    }

    @Data
    public static class CreatePollRequest {
        private String title;
        private List<String> options;
    }
}
