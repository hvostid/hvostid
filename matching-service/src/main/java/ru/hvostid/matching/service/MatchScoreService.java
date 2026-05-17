package ru.hvostid.matching.service;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportServiceClient;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.dto.MatchScoreResponse;
import ru.hvostid.matching.entity.BuyerQuestionnaire;
import ru.hvostid.matching.exception.QuestionnaireNotFoundException;
import ru.hvostid.matching.repository.BuyerQuestionnaireRepository;

@Service
public class MatchScoreService {
    private static final Logger log = LoggerFactory.getLogger(MatchScoreService.class);

    private final BuyerQuestionnaireRepository questionnaireRepository;
    private final ListingServiceClient listingClient;
    private final PassportServiceClient passportClient;
    private final CompatibilityScoreCalculator calculator;

    public MatchScoreService(
            BuyerQuestionnaireRepository questionnaireRepository,
            ListingServiceClient listingClient,
            PassportServiceClient passportClient,
            CompatibilityScoreCalculator calculator) {
        this.questionnaireRepository = questionnaireRepository;
        this.listingClient = listingClient;
        this.passportClient = passportClient;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public MatchScoreResponse calculateScore(long listingId, long userId, String requestId) {
        log.debug("Calculating match score listingId={} userId={}", listingId, userId);

        BuyerQuestionnaire questionnaire = questionnaireRepository
                .findByUserId(userId)
                .orElseThrow(() -> new QuestionnaireNotFoundException("Questionnaire not found for user: " + userId));

        ListingSnapshot listing = listingClient.getListing(listingId, userId, requestId);

        boolean degraded = false;
        Optional<PassportSnapshot> passport = Optional.empty();
        Long passportId = parsePassportId(listing.passportId());
        if (passportId == null) {
            degraded = true;
            log.warn("Listing {} has no valid passportId", listingId);
        } else {
            passport = passportClient.getPassport(passportId, requestId);
            if (passport.isEmpty()) {
                degraded = true;
            }
        }

        PetContext petContext = PetContext.from(listing, passport);
        CompatibilityResult result = calculator.calculate(questionnaire, petContext);

        log.info(
                "Match score calculated listingId={} userId={} score={} level={} degraded={}",
                listingId,
                userId,
                result.score(),
                result.level(),
                degraded);

        return MatchScoreResponse.from(result, degraded);
    }

    private Long parsePassportId(String passportId) {
        if (passportId == null || passportId.isBlank()) {
            return null;
        }
        String digits = passportId.trim();
        if (digits.startsWith("passport-")) {
            digits = digits.substring("passport-".length());
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
