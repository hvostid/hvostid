package ru.hvostid.matching.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.matching.entity.BuyerQuestionnaire;

import java.util.Optional;

public interface BuyerQuestionnaireRepository extends JpaRepository<BuyerQuestionnaire, Long> {
    Optional<BuyerQuestionnaire> findByUserId(Long userId);
}
