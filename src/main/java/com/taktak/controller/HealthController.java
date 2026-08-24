package com.taktak.controller;

import com.taktak.repository.PartyQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final PartyQuestionRepository partyQuestionRepository;

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/api/health/questions")
    public Map<String, Long> questionCounts() {
        return Map.of(
                "total", partyQuestionRepository.count(),
                "truth", partyQuestionRepository.countByCategory("TRUTH"),
                "action", partyQuestionRepository.countByCategory("ACTION"),
                "quiz", partyQuestionRepository.countByCategory("QUIZ")
        );
    }
}
