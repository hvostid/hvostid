package ru.hvostid.passport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import ru.hvostid.passport.entity.Vaccination;

@Schema(description = "Vaccination entry attached to a pet passport")
public record VaccinationResponse(Long id, String name, LocalDate date, LocalDate nextDate, boolean verified) {
    public static VaccinationResponse from(Vaccination vaccination) {
        return new VaccinationResponse(
                vaccination.getId(),
                vaccination.getName(),
                vaccination.getDate(),
                vaccination.getNextDate(),
                vaccination.isVerified());
    }
}
