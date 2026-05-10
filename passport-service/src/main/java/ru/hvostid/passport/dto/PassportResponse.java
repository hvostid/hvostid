package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ru.hvostid.passport.entity.Gender;
import ru.hvostid.passport.entity.PetPassport;

@Schema(description = "Pet passport response")
public record PassportResponse(
        Long id,
        Long sellerId,
        String species,
        String breed,
        String name,
        LocalDate birthDate,
        Gender gender,
        String color,
        String temperament,
        String specialNeeds,
        boolean neutered,
        boolean microchipped,
        Instant createdAt,
        Instant updatedAt,
        List<VaccinationResponse> vaccinations) {
    public static PassportResponse from(PetPassport passport) {
        return new PassportResponse(
                passport.getId(),
                passport.getSellerId(),
                passport.getSpecies(),
                passport.getBreed(),
                passport.getName(),
                passport.getBirthDate(),
                passport.getGender(),
                passport.getColor(),
                passport.getTemperament(),
                passport.getSpecialNeeds(),
                passport.isNeutered(),
                passport.isMicrochipped(),
                passport.getCreatedAt(),
                passport.getUpdatedAt(),
                passport.getVaccinations().stream()
                        .map(VaccinationResponse::from)
                        .toList());
    }
}
