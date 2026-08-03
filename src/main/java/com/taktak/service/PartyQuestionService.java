package com.taktak.service;

import com.taktak.model.PartyQuestion;
import com.taktak.repository.PartyQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PartyQuestionService {

    private final PartyQuestionRepository repository;
    private final Random random = new Random();

    public PartyQuestion getRandomQuizQuestion() {
        List<PartyQuestion> quizList = repository.findByCategory("QUIZ");
        if (quizList.isEmpty()) {
            return PartyQuestion.builder()
                    .category("QUIZ")
                    .theme("general")
                    .prompt("Quel pays a remporté la Coupe du Monde 1978 en battant le Mexique 3-1 ?")
                    .answer("La Tunisie.")
                    .discussion("Cet exploit est-il le plus mémorable de l'histoire du football tunisien ?")
                    .build();
        }
        return quizList.get(random.nextInt(quizList.size()));
    }

    public PartyQuestion getRandomTruth(String theme) {
        String queryTheme = (theme != null && !theme.isBlank()) ? theme : "social";
        List<PartyQuestion> list = repository.findByCategoryAndTheme("TRUTH", queryTheme);
        if (list.isEmpty()) {
            list = repository.findByCategory("TRUTH");
        }
        if (list.isEmpty()) {
            return PartyQuestion.builder()
                    .category("TRUTH")
                    .theme(queryTheme)
                    .prompt("Sra7a 💬: Quel est le plus grand changement dans ta façon de penser ces 2 dernières années ?")
                    .build();
        }
        return list.get(random.nextInt(list.size()));
    }

    public PartyQuestion getRandomAction(String theme) {
        String queryTheme = (theme != null && !theme.isBlank()) ? theme : "social";
        List<PartyQuestion> list = repository.findByCategoryAndTheme("ACTION", queryTheme);
        if (list.isEmpty()) {
            list = repository.findByCategory("ACTION");
        }
        if (list.isEmpty()) {
            return PartyQuestion.builder()
                    .category("ACTION")
                    .theme(queryTheme)
                    .prompt("Action ⚡: Fais une imitation comique de quelqu'un à la table pendant 30 secondes !")
                    .build();
        }
        return list.get(random.nextInt(list.size()));
    }
}
