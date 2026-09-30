package ru.hvostid.passport.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record VaccinationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @PastOrPresent LocalDate date,
        LocalDate nextDate) {
    @AssertTrue(message = "Next vaccination date must not precede vaccination date")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isDateRangeValid() {
        return nextDate == null || date == null || !nextDate.isBefore(date);
    }
}
