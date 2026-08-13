package com.taktak.service;

import com.taktak.model.PartyQuestion;

public interface IPartyQuestionService {
    PartyQuestion getRandomQuizQuestion();
    PartyQuestion getRandomTruth(String theme);
    PartyQuestion getRandomAction(String theme);
}
