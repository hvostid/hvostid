package ru.hvostid.matching.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.matching.dto.QuestionnaireRequest;
import ru.hvostid.matching.dto.QuestionnaireResponse;
import ru.hvostid.matching.entity.BuyerQuestionnaire;
import ru.hvostid.matching.exception.QuestionnaireNotFoundException;
import ru.hvostid.matching.repository.BuyerQuestionnaireRepository;

@Service
public class QuestionnaireService {
    private static final Logger log = LoggerFactory.getLogger(QuestionnaireService.class);

    private final BuyerQuestionnaireRepository repository;

    public QuestionnaireService(BuyerQuestionnaireRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public QuestionnaireResponse upsertQuestionnaire(QuestionnaireRequest request, Long userId) {
        log.debug("Upserting questionnaire for userId={}", userId);

        BuyerQuestionnaire questionnaire =
                repository.findByUserId(userId).orElseGet(() -> new BuyerQuestionnaire(userId));

        applyRequest(questionnaire, request);

        BuyerQuestionnaire saved = repository.save(questionnaire);
        log.info("Questionnaire saved id={} userId={}", saved.getId(), userId);

        return QuestionnaireResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public QuestionnaireResponse getQuestionnaire(Long userId) {
        log.debug("Getting questionnaire for userId={}", userId);

        BuyerQuestionnaire questionnaire = repository
                .findByUserId(userId)
                .orElseThrow(() -> new QuestionnaireNotFoundException("Questionnaire not found for user: " + userId));

        return QuestionnaireResponse.from(questionnaire);
    }

    private void applyRequest(BuyerQuestionnaire q, QuestionnaireRequest r) {
        q.setLivingSpace(r.livingSpace());
        q.setLivingArea(r.livingArea());
        q.setHasYard(r.hasYard());
        q.setHasChildren(r.hasChildren());
        q.setChildrenAgeMin(r.childrenAgeMin());
        q.setHasAllergies(r.hasAllergies());
        q.setAllergyDetails(normalize(r.allergyDetails()));
        q.setPetExperience(r.petExperience());
        q.setActivityLevel(r.activityLevel());
        q.setMonthlyBudget(r.monthlyBudget());
        q.setWorkSchedule(r.workSchedule());
        q.setReadyForAdaptation(r.readyForAdaptation());
        q.setPreferredSpecies(normalize(r.preferredSpecies()));
        q.setPreferredBreed(normalize(r.preferredBreed()));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
