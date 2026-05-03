package ru.hvostid.matching.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.matching.entity.BuyerQuestionnaire;

public interface BuyerQuestionnaireRepository extends JpaRepository<BuyerQuestionnaire, Long> {
    Optional<BuyerQuestionnaire> findByUserId(Long userId);
}
