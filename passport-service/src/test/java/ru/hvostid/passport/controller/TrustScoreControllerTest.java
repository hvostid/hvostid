package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
import static ru.hvostid.common.security.UserRole.BUYER;

import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import ru.hvostid.passport.entity.Gender;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.entity.Vaccination;
import ru.hvostid.passport.repository.PetPassportRepository;
import ru.hvostid.passport.service.TrustScoreService;

@SpringBootTest
@AutoConfigureMockMvc
class TrustScoreControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PetPassportRepository passportRepository;

    @Autowired
    private TrustScoreService trustScoreService;

    @BeforeEach
    void cleanBeforeTest() {
        jdbcTemplate.execute("TRUNCATE TABLE passport_documents, vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    @AfterEach
    void cleanAfterTest() {
        jdbcTemplate.execute("TRUNCATE TABLE passport_documents, vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    @Test
    void minimalPassportReturnsZeroScore() throws Exception {
        Long passportId = persistPassport(false, false);

        mockMvc.perform(get(PASSPORTS_URL + "/{id}/trust", passportId)
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, BUYER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(0)))
                .andExpect(jsonPath("$.breakdown.profileComplete", is(0)))
                .andExpect(jsonPath("$.breakdown.vaccinationsDated", is(0)))
                .andExpect(jsonPath("$.breakdown.moderated", is(0)));
    }

    @Test
    void fullProfileWithModerationAndVaccinationReturnsExpectedScore() throws Exception {
        Long passportId = persistPassport(true, true);

        // 20 (profile complete) + 10 (vaccinations dated) + 5 (moderated) = 35
        mockMvc.perform(get(PASSPORTS_URL + "/{id}/trust", passportId)
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, BUYER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score", is(35)))
                .andExpect(jsonPath("$.breakdown.profileComplete", is(20)))
                .andExpect(jsonPath("$.breakdown.vaccinationsDated", is(10)))
                .andExpect(jsonPath("$.breakdown.moderated", is(5)));
    }

    @Test
    void cachedScoreIsUpdatedByRecalculate() {
        Long passportId = persistPassport(true, true);

        trustScoreService.recalculate(passportId);

        PetPassport reloaded =
                passportRepository.findById(passportId).orElseThrow(() -> new AssertionError("Passport missing"));
        org.assertj.core.api.Assertions.assertThat(reloaded.getTrustScore()).isEqualTo(35);
    }

    @Test
    void trustEndpointRequiresAuthentication() throws Exception {
        Long passportId = persistPassport(false, false);

        mockMvc.perform(get(PASSPORTS_URL + "/{id}/trust", passportId)).andExpect(status().isUnauthorized());
    }

    @Test
    void trustEndpointReturns404ForUnknownPassport() throws Exception {
        mockMvc.perform(get(PASSPORTS_URL + "/{id}/trust", 999_999L)
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, BUYER.value()))
                .andExpect(status().isNotFound());
    }

    private Long persistPassport(boolean withFullProfile, boolean moderatedWithVaccination) {
        PetPassport.Builder builder = PetPassport.builder()
                .sellerId(10L)
                .species("dog")
                .name("Rex")
                .birthDate(LocalDate.of(2023, 5, 10))
                .gender(Gender.MALE)
                .neutered(true)
                .microchipped(false);
        if (withFullProfile) {
            builder.breed("Husky").color("grey");
        }
        PetPassport passport = builder.build();
        passport.setModerated(moderatedWithVaccination);
        PetPassport saved = passportRepository.save(passport);
        if (moderatedWithVaccination) {
            saved.getVaccinations().add(new Vaccination(saved, "Rabies", LocalDate.of(2026, 1, 1), null, true));
            saved = passportRepository.save(saved);
        }
        return saved.getId();
    }
}
