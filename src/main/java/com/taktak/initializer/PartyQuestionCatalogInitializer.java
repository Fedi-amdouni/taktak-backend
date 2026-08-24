package com.taktak.initializer;

import com.taktak.repository.PartyQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@ConditionalOnProperty(
        name = "taktak.party-questions.sync-on-startup",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PartyQuestionCatalogInitializer implements CommandLineRunner {
    private static final long EXPECTED_TOTAL = 700;
    private static final long EXPECTED_TRUTH = 200;
    private static final long EXPECTED_ACTION = 200;
    private static final long EXPECTED_QUIZ = 300;

    private final DataSource dataSource;
    private final PartyQuestionRepository partyQuestionRepository;

    @Override
    public void run(String... args) {
        log.info("Synchronisation obligatoire du catalogue canonique des questions...");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("party_questions_seeds_derja.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);

        long total = partyQuestionRepository.count();
        long truth = partyQuestionRepository.countByCategory("TRUTH");
        long action = partyQuestionRepository.countByCategory("ACTION");
        long quiz = partyQuestionRepository.countByCategory("QUIZ");
        if (total != EXPECTED_TOTAL || truth != EXPECTED_TRUTH || action != EXPECTED_ACTION || quiz != EXPECTED_QUIZ) {
            throw new IllegalStateException("Catalogue de questions invalide après synchronisation: total=" + total
                    + ", truth=" + truth + ", action=" + action + ", quiz=" + quiz);
        }

        log.info("Catalogue canonique vérifié: 700 questions (200 vérités, 200 actions, 300 quiz).");
    }
}
