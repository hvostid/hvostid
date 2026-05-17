package ru.hvostid.matching.service;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.hvostid.matching.client.ListingServiceClient;
import ru.hvostid.matching.client.ListingSnapshot;
import ru.hvostid.matching.client.PassportServiceClient;
import ru.hvostid.matching.client.PassportSnapshot;
import ru.hvostid.matching.config.CacheConfig;
import ru.hvostid.matching.domain.CompatibilityResult;
import ru.hvostid.matching.domain.DegradedReason;
import ru.hvostid.matching.domain.PetContext;
import ru.hvostid.matching.dto.AdaptationPhaseDto;
import ru.hvostid.matching.dto.FactorScoreDto;
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
    private final MatchExplanationService explanationService;
    private final AdaptationPlanBuilder adaptationPlanBuilder;

    public MatchScoreService(
            BuyerQuestionnaireRepository questionnaireRepository,
            ListingServiceClient listingClient,
            PassportServiceClient passportClient,
            CompatibilityScoreCalculator calculator,
            MatchExplanationService explanationService,
            AdaptationPlanBuilder adaptationPlanBuilder) {
        this.questionnaireRepository = questionnaireRepository;
        this.listingClient = listingClient;
        this.passportClient = passportClient;
        this.calculator = calculator;
        this.explanationService = explanationService;
        this.adaptationPlanBuilder = adaptationPlanBuilder;
    }

    @Cacheable(cacheNames = CacheConfig.MATCH_SCORES_CACHE, key = "#userId + '_' + #listingId")
    public MatchScoreResponse calculateScore(long listingId, long userId, String requestId) {
        log.debug("Calculating match score listingId={} userId={} requestId={}", listingId, userId, requestId);

        BuyerQuestionnaire questionnaire = requireQuestionnaire(userId);

        ListingSnapshot listing = listingClient.getListing(listingId, userId, requestId);

        DegradedReason degradedReason = null;
        Optional<PassportSnapshot> passport = Optional.empty();
        Long passportId = parsePassportId(listing.passportId());
        if (passportId == null) {
            degradedReason = DegradedReason.PASSPORT_ID_UNPARSEABLE;
            log.warn(
                    "Listing {} has unparseable passportId='{}' requestId={}",
                    listingId,
                    listing.passportId(),
                    requestId);
        } else {
            passport = passportClient.getPassport(passportId, requestId);
            if (passport.isEmpty()) {
                degradedReason = DegradedReason.PASSPORT_UNAVAILABLE;
                log.warn(
                        "Passport unavailable for listingId={} passportId={} requestId={}",
                        listingId,
                        passportId,
                        requestId);
            }
        }

        PetContext petContext = PetContext.from(listing, passport);
        if (petContext.speciesUnknown()) {
            degradedReason = pickDegradedReason(degradedReason, DegradedReason.SPECIES_UNKNOWN);
            log.warn("Unknown species for listingId={} requestId={}", listingId, requestId);
        }

        CompatibilityResult result = calculator.calculate(questionnaire, petContext);
        boolean degraded = degradedReason != null;

        String summary = explanationService.buildSummary(petContext, result, degraded);
        List<String> tips = explanationService.buildTips(petContext, result);
        List<AdaptationPhaseDto> adaptationPlan = adaptationPlanBuilder.build(petContext);
        List<FactorScoreDto> factors =
                result.factors().stream().map(FactorScoreDto::from).toList();
        String reasonCode = degradedReason == null ? null : degradedReason.code();

        log.info(
                "Match score calculated listingId={} userId={} requestId={} score={} level={} degraded={} degradedReason={}",
                listingId,
                userId,
                requestId,
                result.score(),
                result.level(),
                degraded,
                degradedReason);

        return new MatchScoreResponse(
                result.score(), result.level(), factors, summary, tips, adaptationPlan, degraded, reasonCode);
    }

    BuyerQuestionnaire requireQuestionnaire(long userId) {
        return questionnaireRepository
                .findByUserId(userId)
                .orElseThrow(() -> new QuestionnaireNotFoundException("Questionnaire not found for user: " + userId));
    }

    private static DegradedReason pickDegradedReason(DegradedReason current, DegradedReason next) {
        if (current == null) {
            return next;
        }
        return current;
    }

    static Long parsePassportId(String passportId) {
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
