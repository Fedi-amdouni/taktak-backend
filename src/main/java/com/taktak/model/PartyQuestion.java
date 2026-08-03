package com.taktak.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "party_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PartyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category; // "QUIZ", "TRUTH", "ACTION"

    @Column(nullable = false)
    private String theme; // "intimate", "social", "friends", "future", "general"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String prompt; // Question text or Action dare description

    @Column(columnDefinition = "TEXT")
    private String answer; // Answer for Quiz questions

    @Column(columnDefinition = "TEXT")
    private String discussion; // Discussion prompt for Quiz questions
}
