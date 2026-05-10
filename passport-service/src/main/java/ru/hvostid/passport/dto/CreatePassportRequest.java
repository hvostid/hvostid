package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import ru.hvostid.passport.entity.Gender;

@Schema(description = "Pet passport creation request")
public record CreatePassportRequest(
        @NotBlank @Size(max = 255) String species,
        @Size(max = 255) String breed,
        @NotBlank @Size(max = 255) String name,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Gender gender,
        @Size(max = 255) String color,
        @Size(max = 1000) String temperament,
        @Size(max = 1000) String specialNeeds,
        @NotNull Boolean neutered,
        @NotNull Boolean microchipped) {}
