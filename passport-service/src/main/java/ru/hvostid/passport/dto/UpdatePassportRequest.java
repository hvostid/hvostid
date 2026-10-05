package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import ru.hvostid.passport.entity.Gender;

@Schema(description = "Pet passport update request. Omitted fields remain unchanged.")
public record UpdatePassportRequest(
        @Size(max = 255) String species,
        @Size(max = 255) String breed,
        @Size(max = 255) String name,
        @PastOrPresent LocalDate birthDate,
        Gender gender,
        @Size(max = 255) String color,
        @Size(max = 1000) String temperament,
        @Size(max = 1000) String specialNeeds,
        Boolean neutered,
        Boolean microchipped,
        @Size(max = 100) List<@Valid VaccinationRequest> vaccinations) {
    public UpdatePassportRequest(
            String species,
            String breed,
            String name,
            LocalDate birthDate,
            Gender gender,
            String color,
            String temperament,
            String specialNeeds,
            Boolean neutered,
            Boolean microchipped) {
        this(species, breed, name, birthDate, gender, color, temperament, specialNeeds, neutered, microchipped, null);
    }
}
