package ru.hvostid.passport.service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.dto.TrustScoreBreakdown;
import ru.hvostid.passport.dto.TrustScoreResponse;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PassportDocumentRepository;
import ru.hvostid.passport.repository.PetPassportRepository;

@Service
public class TrustScoreService {
    private static final Logger log = LoggerFactory.getLogger(TrustScoreService.class);

    private final PetPassportRepository passportRepository;
    private final PassportDocumentRepository documentRepository;
    private final SellerSignalsProvider sellerSignalsProvider;
    private final ListingServiceClient listingServiceClient;
    private final TrustScoreCalculator calculator;

    public TrustScoreService(
            PetPassportRepository passportRepository,
            PassportDocumentRepository documentRepository,
            SellerSignalsProvider sellerSignalsProvider,
            ListingServiceClient listingServiceClient,
            TrustScoreCalculator calculator) {
        this.passportRepository = passportRepository;
        this.documentRepository = documentRepository;
        this.sellerSignalsProvider = sellerSignalsProvider;
        this.listingServiceClient = listingServiceClient;
        this.calculator = calculator;
    }

    /**
     * Returns the trust score for a passport. Access is granted when the caller is
     * the owner or has MODERATOR/ADMIN role; otherwise the passport must be referenced
     * by at least one PUBLISHED listing so we do not leak passport existence to anonymous
     * id enumeration. The PUBLISHED check is delegated to listing-service.
     *
     * <p>Returns 404 in both "not found" and "not viewable" cases to avoid disclosing
     * which of the two applies.
     *
     * <p>Intentionally not annotated with {@code @Transactional}: the access check makes a
     * synchronous HTTP call to listing-service, which would otherwise hold a database
     * connection from Hikari for the full duration of the remote round-trip. Each
     * repository call below opens its own short transaction via Spring Data JPA defaults.
     */
    public TrustScoreResponse getTrustScore(Long passportId, Long userId, Set<String> userRoles, String requestId) {
        // findWithVaccinationsById eagerly fetches vaccinations via @EntityGraph,
        // so the returned entity (although detached after the call) is safe to read
        // in the rest of this method without an enclosing transaction.
        PetPassport passport = passportRepository
                .findWithVaccinationsById(passportId)
                .orElseThrow(() -> new PassportNotFoundException("Passport not found with id: " + passportId));

        if (!canReadTrustScoreWithoutListingCheck(passport, userId, userRoles)
                && !listingServiceClient.hasPublishedListingForPassport(passport.getId(), requestId)) {
            log.warn(
                    "Trust score access denied for passportId={} userId={} (no PUBLISHED listing reference)",
                    passportId,
                    userId);
            // Same response shape as a missing passport so anonymous probers
            // cannot tell unpublished passports apart from non-existent ones.
            throw new PassportNotFoundException("Passport not found with id: " + passportId);
        }

        TrustScoreBreakdown breakdown = computeBreakdown(passport);
        return new TrustScoreResponse(breakdown.total(), breakdown);
    }

    private static boolean canReadTrustScoreWithoutListingCheck(
            PetPassport passport, Long userId, Set<String> userRoles) {
        if (userId != null && userId.equals(passport.getSellerId())) {
            return true;
        }
        return userRoles != null
                && (userRoles.contains(UserRole.ADMIN.value()) || userRoles.contains(UserRole.MODERATOR.value()));
    }

    /**
     * Recomputes the cached trust score for the passport and persists it.
     * No-op if the passport does not exist (e.g. concurrent deletion).
     */
    @Transactional
    public void recalculate(Long passportId) {
        PetPassport passport =
                passportRepository.findWithVaccinationsById(passportId).orElse(null);
        if (passport == null) {
            log.debug("Skipping trust-score recalculation: passportId={} no longer exists", passportId);
            return;
        }
        int score = computeBreakdown(passport).total();
        if (passport.getTrustScore() != score) {
            passport.setTrustScore(score);
            passportRepository.save(passport);
            log.info("Recomputed trust score passportId={} score={}", passportId, score);
        }
    }

    private TrustScoreBreakdown computeBreakdown(PetPassport passport) {
        List<PassportDocument> documents = documentRepository.findByPassportIdOrderByUploadedAtDesc(passport.getId());
        SellerSignals signals = sellerSignalsProvider.fetch(passport.getSellerId());
        return calculator.compute(passport, documents, signals);
    }
}
