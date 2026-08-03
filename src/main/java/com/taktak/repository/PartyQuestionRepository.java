package com.taktak.repository;

import com.taktak.model.PartyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartyQuestionRepository extends JpaRepository<PartyQuestion, Long> {

    List<PartyQuestion> findByCategory(String category);

    List<PartyQuestion> findByCategoryAndTheme(String category, String theme);

    long countByCategory(String category);
}
