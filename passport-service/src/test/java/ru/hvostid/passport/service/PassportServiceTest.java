package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import ru.hvostid.passport.dto.CreatePassportRequest;
import ru.hvostid.passport.dto.PassportResponse;
import ru.hvostid.passport.dto.UpdatePassportRequest;
import ru.hvostid.passport.entity.Gender;
import ru.hvostid.passport.exception.PassportAccessDeniedException;
import ru.hvostid.passport.exception.PassportNotFoundException;

@SpringBootTest
@Transactional
class PassportServiceTest extends AbstractPassportIntegrationTest {
    @Autowired
    private PassportService passportService;

    @Test
    void createPassportUsesSellerIdFromAuthenticatedUser() {
        PassportResponse response = passportService.createPassport(validCreateRequest(), 42L);

        assertThat(response.id()).isNotNull();
        assertThat(response.sellerId()).isEqualTo(42L);
        assertThat(response.species()).isEqualTo("dog");
        assertThat(response.name()).isEqualTo("Rex");
        assertThat(response.vaccinations()).isEmpty();
    }

    @Test
    void updatePassportAllowsOwnerPartialUpdate() {
        PassportResponse created = passportService.createPassport(validCreateRequest(), 42L);
        UpdatePassportRequest request =
                new UpdatePassportRequest(null, null, null, null, null, null, "calm, good with kids", null, null, true);

        PassportResponse updated = passportService.updatePassport(created.id(), request, 42L);

        assertThat(updated.name()).isEqualTo("Rex");
        assertThat(updated.temperament()).isEqualTo("calm, good with kids");
        assertThat(updated.microchipped()).isTrue();
    }

    @Test
    void updatePassportRejectsNonOwner() {
        PassportResponse created = passportService.createPassport(validCreateRequest(), 42L);
        UpdatePassportRequest request =
                new UpdatePassportRequest(null, null, null, null, null, null, "changed", null, null, null);

        assertThatThrownBy(() -> passportService.updatePassport(created.id(), request, 43L))
                .isInstanceOf(PassportAccessDeniedException.class);
    }

    @Test
    void getPassportRejectsMissingPassport() {
        assertThatThrownBy(() -> passportService.getPassport(999L)).isInstanceOf(PassportNotFoundException.class);
    }

    private CreatePassportRequest validCreateRequest() {
        return new CreatePassportRequest(
                " dog ",
                " Husky ",
                " Rex ",
                LocalDate.parse("2023-05-10"),
                Gender.MALE,
                " grey-white ",
                " active, friendly ",
                null,
                true,
                false);
    }
}
