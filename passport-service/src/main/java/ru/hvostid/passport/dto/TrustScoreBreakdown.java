package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Per-component contributions that make up the trust score")
public record TrustScoreBreakdown(
        @Schema(description = "All key passport fields filled in", example = "20")
        int profileComplete,

        @Schema(description = "At least one PHOTO document uploaded", example = "15")
        int hasPhoto,

        @Schema(description = "Vaccination certificate document uploaded", example = "0")
        int hasVaccinationCert,

        @Schema(description = "Vet record document uploaded", example = "15")
        int hasVetRecord,

        @Schema(description = "At least one dated vaccination entry", example = "10")
        int vaccinationsDated,

        @Schema(description = "Seller average rating is 4.0 or higher", example = "10")
        int sellerRating,

        @Schema(description = "Seller has more than 3 successful sales", example = "0")
        int sellerSales,

        @Schema(description = "Passport has passed moderation", example = "5")
        int moderated) {
    public int total() {
        return profileComplete
                + hasPhoto
                + hasVaccinationCert
                + hasVetRecord
                + vaccinationsDated
                + sellerRating
                + sellerSales
                + moderated;
    }
}
