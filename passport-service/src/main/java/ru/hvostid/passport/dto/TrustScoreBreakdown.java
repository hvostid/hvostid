package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Per-component contributions that make up the trust score")
public record TrustScoreBreakdown(
        @Schema(description = "All key passport fields filled in", example = "25")
        int profileComplete,

        @Schema(description = "At least one PHOTO document uploaded", example = "20")
        int hasPhoto,

        @Schema(description = "Vaccination certificate document uploaded", example = "0")
        int hasVaccinationCert,

        @Schema(description = "Vet record document uploaded", example = "15")
        int hasVetRecord,

        @Schema(description = "At least one dated vaccination entry", example = "15")
        int vaccinationsDated,

        @Schema(description = "Deprecated, unsupported component; always zero", example = "0", deprecated = true)
        int sellerRating,

        @Schema(description = "Deprecated, unsupported component; always zero", example = "0", deprecated = true)
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
