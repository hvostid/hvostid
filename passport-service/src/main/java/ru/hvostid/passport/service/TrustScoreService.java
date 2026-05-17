package ru.hvostid.passport.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final TrustScoreCalculator calculator;

    public TrustScoreService(
            PetPassportRepository passportRepository,
            PassportDocumentRepository documentRepository,
            SellerSignalsProvider sellerSignalsProvider,
            TrustScoreCalculator calculator) {
        this.passportRepository = passportRepository;
        this.documentRepository = documentRepository;
        this.sellerSignalsProvider = sellerSignalsProvider;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public TrustScoreResponse getTrustScore(Long passportId) {
        PetPassport passport = passportRepository
                .findWithVaccinationsById(passportId)
                .orElseThrow(() -> new PassportNotFoundException("Passport not found with id: " + passportId));
        TrustScoreBreakdown breakdown = computeBreakdown(passport);
        return new TrustScoreResponse(breakdown.total(), breakdown);
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
