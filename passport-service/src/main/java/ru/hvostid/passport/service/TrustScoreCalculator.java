package ru.hvostid.passport.service;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.hvostid.passport.dto.TrustScoreBreakdown;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.entity.PetPassport;

/**
 * Pure function that maps a passport, its documents and external seller
 * signals to a {@link TrustScoreBreakdown}. Total adds up to at most 100.
 */
@Component
public class TrustScoreCalculator {
    static final int POINTS_PROFILE_COMPLETE = 20;
    static final int POINTS_HAS_PHOTO = 15;
    static final int POINTS_HAS_VACCINATION_CERT = 15;
    static final int POINTS_HAS_VET_RECORD = 15;
    static final int POINTS_VACCINATIONS_DATED = 10;
    static final int POINTS_SELLER_RATING = 10;
    static final int POINTS_SELLER_SALES = 10;
    static final int POINTS_MODERATED = 5;

    static final double SELLER_RATING_THRESHOLD = 4.0;
    static final int SELLER_SALES_THRESHOLD = 3;

    public TrustScoreBreakdown compute(PetPassport passport, List<PassportDocument> documents, SellerSignals signals) {
        int profileComplete = isProfileComplete(passport) ? POINTS_PROFILE_COMPLETE : 0;
        int hasPhoto = hasDocument(documents, PassportDocumentType.PHOTO) ? POINTS_HAS_PHOTO : 0;
        int hasVaccinationCert =
                hasDocument(documents, PassportDocumentType.VACCINATION_CERT) ? POINTS_HAS_VACCINATION_CERT : 0;
        int hasVetRecord = hasDocument(documents, PassportDocumentType.VET_RECORD) ? POINTS_HAS_VET_RECORD : 0;
        int vaccinationsDated = passport.getVaccinations() != null
                        && !passport.getVaccinations().isEmpty()
                ? POINTS_VACCINATIONS_DATED
                : 0;
        int sellerRating =
                signals.rating() != null && signals.rating() >= SELLER_RATING_THRESHOLD ? POINTS_SELLER_RATING : 0;
        int sellerSales =
                signals.salesCount() != null && signals.salesCount() > SELLER_SALES_THRESHOLD ? POINTS_SELLER_SALES : 0;
        int moderated = passport.isModerated() ? POINTS_MODERATED : 0;

        return new TrustScoreBreakdown(
                profileComplete,
                hasPhoto,
                hasVaccinationCert,
                hasVetRecord,
                vaccinationsDated,
                sellerRating,
                sellerSales,
                moderated);
    }

    private boolean isProfileComplete(PetPassport passport) {
        return isFilled(passport.getSpecies())
                && isFilled(passport.getName())
                && passport.getBirthDate() != null
                && passport.getGender() != null
                && isFilled(passport.getBreed())
                && isFilled(passport.getColor());
    }

    private boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasDocument(List<PassportDocument> documents, PassportDocumentType type) {
        return documents.stream().anyMatch(doc -> doc.getType() == type);
    }
}
