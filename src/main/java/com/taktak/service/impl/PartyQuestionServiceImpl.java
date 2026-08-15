package com.taktak.service.impl;

import com.taktak.model.PartyQuestion;
import com.taktak.repository.PartyQuestionRepository;
import com.taktak.service.IPartyQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PartyQuestionServiceImpl implements IPartyQuestionService {

    private final PartyQuestionRepository repository;
    private final Random random = new Random();

    @Override
    public PartyQuestion getRandomQuizQuestion() {
        List<PartyQuestion> quizList = repository.findByCategory("QUIZ");
        if (quizList.isEmpty()) {
            return PartyQuestion.builder()
                    .category("QUIZ")
                    .theme("general")
                    .prompt("شكون هو هداف المنتخب التونسي التاريخي في كل المسابقات الرسمية؟")
                    .answer("عصام جمعة (Issam Jemâa بـ 36 هدف)")
                    .discussion("برشا ناس كانت تنبر على عصام جمعة وتوا يقولو ليتنا نلقاو مهاجم كيفو! شنوة رأيكم؟")
                    .build();
        }
        return quizList.get(random.nextInt(quizList.size()));
    }

    @Override
    public PartyQuestion getRandomTruth(String theme) {
        String queryTheme = (theme != null && !theme.isBlank()) ? theme.trim().toLowerCase() : "all";
        List<PartyQuestion> list;
        if ("all".equals(queryTheme)) {
            list = repository.findByCategory("TRUTH");
        } else {
            list = repository.findByCategoryAndTheme("TRUTH", queryTheme);
            if (list.isEmpty()) {
                list = repository.findByCategory("TRUTH");
            }
        }
        if (list.isEmpty()) {
            return PartyQuestion.builder()
                    .category("TRUTH")
                    .theme(queryTheme)
                    .prompt("شنوة أكثر حاجة تخاف منها وماتحبش تستعرف باها لصحابك؟")
                    .build();
        }
        return list.get(random.nextInt(list.size()));
    }

    @Override
    public PartyQuestion getRandomAction(String theme) {
        String queryTheme = (theme != null && !theme.isBlank()) ? theme.trim().toLowerCase() : "all";
        List<PartyQuestion> list;
        if ("all".equals(queryTheme)) {
            list = repository.findByCategory("ACTION");
        } else {
            list = repository.findByCategoryAndTheme("ACTION", queryTheme);
            if (list.isEmpty()) {
                list = repository.findByCategory("ACTION");
            }
        }
        if (list.isEmpty()) {
            return PartyQuestion.builder()
                    .category("ACTION")
                    .theme(queryTheme)
                    .prompt("اعمل تقليد مضحك لأكثر فازة يتعصب منها صاحبك اللي على يمينك!")
                    .build();
        }
        return list.get(random.nextInt(list.size()));
    }
}
