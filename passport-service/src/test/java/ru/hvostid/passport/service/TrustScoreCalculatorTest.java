package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.hvostid.passport.dto.TrustScoreBreakdown;
import ru.hvostid.passport.entity.Gender;
import ru.hvostid.passport.entity.PassportDocument;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.entity.Vaccination;

class TrustScoreCalculatorTest {
    private final TrustScoreCalculator calculator = new TrustScoreCalculator();

    @Test
    void scoreIsZeroForEmptySignalsAndMinimalPassport() {
        PetPassport passport = passport(false);
        passport.setBreed(null);
        passport.setColor(null);

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.total()).isZero();
        assertThat(breakdown.profileComplete()).isZero();
        assertThat(breakdown.hasPhoto()).isZero();
        assertThat(breakdown.hasVaccinationCert()).isZero();
        assertThat(breakdown.hasVetRecord()).isZero();
        assertThat(breakdown.vaccinationsDated()).isZero();
        assertThat(breakdown.sellerRating()).isZero();
        assertThat(breakdown.sellerSales()).isZero();
        assertThat(breakdown.moderated()).isZero();
    }

    @Test
    void profileCompleteRequiresBreedAndColor() {
        PetPassport passport = passport(false);
        passport.setBreed("Husky");
        passport.setColor("grey");

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.profileComplete()).isEqualTo(25);
    }

    @Test
    void profileIncompleteWhenBlankFieldPresent() {
        PetPassport passport = passport(false);
        passport.setBreed("  ");
        passport.setColor("grey");

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.profileComplete()).isZero();
    }

    @Test
    void countsEachDocumentTypeIndependently() {
        PetPassport passport = passport(false);
        passport.setBreed("Husky");
        passport.setColor("grey");
        List<PassportDocument> documents = List.of(
                document(passport, PassportDocumentType.PHOTO),
                document(passport, PassportDocumentType.VACCINATION_CERT));

        TrustScoreBreakdown breakdown = calculator.compute(passport, documents);

        assertThat(breakdown.hasPhoto()).isEqualTo(20);
        assertThat(breakdown.hasVaccinationCert()).isEqualTo(20);
        assertThat(breakdown.hasVetRecord()).isZero();
    }

    @Test
    void vaccinationsDatedRewardedWhenAtLeastOnePresent() {
        PetPassport passport = passport(false);
        Vaccination v = new Vaccination(passport, "Rabies", LocalDate.of(2026, 1, 1), null, true);
        passport.getVaccinations().add(v);

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.vaccinationsDated()).isEqualTo(15);
    }

    @Test
    void unavailableSellerSignalsDoNotAffectScore() {
        PetPassport passport = passport(false);

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.sellerRating()).isZero();
        assertThat(breakdown.sellerSales()).isZero();
    }

    @Test
    void sellerRatingAndSalesNotAwardedBelowOrAtThresholds() {
        PetPassport passport = passport(false);

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.sellerRating()).isZero();
        assertThat(breakdown.sellerSales()).isZero();
    }

    @Test
    void moderatedPassportAddsFivePoints() {
        PetPassport passport = passport(true);

        TrustScoreBreakdown breakdown = calculator.compute(passport, List.of());

        assertThat(breakdown.moderated()).isEqualTo(5);
    }

    @Test
    void totalCanReachOneHundred() {
        PetPassport passport = passport(true);
        passport.setBreed("Husky");
        passport.setColor("grey");
        passport.getVaccinations().add(new Vaccination(passport, "Rabies", LocalDate.of(2026, 1, 1), null, true));
        List<PassportDocument> documents = List.of(
                document(passport, PassportDocumentType.PHOTO),
                document(passport, PassportDocumentType.VACCINATION_CERT),
                document(passport, PassportDocumentType.VET_RECORD));

        TrustScoreBreakdown breakdown = calculator.compute(passport, documents);

        assertThat(breakdown.total()).isEqualTo(100);
    }

    private static PetPassport passport(boolean moderated) {
        PetPassport passport = PetPassport.builder()
                .sellerId(7L)
                .species("dog")
                .name("Rex")
                .birthDate(LocalDate.of(2023, 5, 10))
                .gender(Gender.MALE)
                .neutered(true)
                .microchipped(false)
                .build();
        passport.setModerated(moderated);
        return passport;
    }

    private static PassportDocument document(PetPassport passport, PassportDocumentType type) {
        return new PassportDocument(passport, type, "file." + type, "path/" + type, "image/jpeg", 100L);
    }
}
