package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
import static ru.hvostid.common.security.UserRole.ADMIN;
import static ru.hvostid.common.security.UserRole.MODERATOR;
import static ru.hvostid.common.security.UserRole.SELLER;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.passport.AbstractPassportIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class PassportControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    private String validRequestBody() {
        return """
                {
                    "species": "dog",
                    "breed": "Husky",
                    "name": "Rex",
                    "birthDate": "2023-05-10",
                    "gender": "MALE",
                    "color": "grey-white",
                    "temperament": "active, friendly",
                    "specialNeeds": null,
                    "neutered": true,
                    "microchipped": false
                }
                """;
    }

    @Nested
    @DisplayName("POST /api/v1/passports")
    class CreateTests {
        @Test
        @DisplayName("seller creates passport - returns 201")
        void create_seller_returns201() throws Exception {
            mockMvc.perform(post(PASSPORTS_URL)
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.sellerId", is(10)))
                    .andExpect(jsonPath("$.species", is("dog")))
                    .andExpect(jsonPath("$.breed", is("Husky")))
                    .andExpect(jsonPath("$.name", is("Rex")))
                    .andExpect(jsonPath("$.birthDate", is("2023-05-10")))
                    .andExpect(jsonPath("$.gender", is("MALE")))
                    .andExpect(jsonPath("$.neutered", is(true)))
                    .andExpect(jsonPath("$.microchipped", is(false)))
                    .andExpect(jsonPath("$.createdAt", notNullValue()))
                    .andExpect(jsonPath("$.updatedAt", notNullValue()))
                    .andExpect(jsonPath("$.vaccinations", hasSize(0)));
        }

        @Test
        @DisplayName("missing required field - returns 400")
        void create_missingRequiredField_returns400() throws Exception {
            String body = validRequestBody().replace("\"species\": \"dog\",", "");

            mockMvc.perform(post(PASSPORTS_URL)
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Validation failed")))
                    .andExpect(jsonPath("$.fieldErrors.species", notNullValue()));
        }

        @Test
        @DisplayName("no user header - returns 401")
        void create_noUserHeader_returns401() throws Exception {
            mockMvc.perform(post(PASSPORTS_URL)
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/passports/{petId}")
    class GetTests {
        @Test
        @DisplayName("owner gets passport with vaccinations - returns 200")
        void get_owner_returnsPassportWithVaccinations() throws Exception {
            createPassport();
            jdbcTemplate.update("""
                    INSERT INTO vaccinations (passport_id, name, date, next_date, verified)
                    VALUES (1, 'rabies', '2024-01-15', '2025-01-15', true)
                    """);

            mockMvc.perform(get(PASSPORTS_URL + "/1").header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.sellerId", is(10)))
                    .andExpect(jsonPath("$.name", is("Rex")))
                    .andExpect(jsonPath("$.vaccinations", hasSize(1)))
                    .andExpect(jsonPath("$.vaccinations[0].name", is("rabies")))
                    .andExpect(jsonPath("$.vaccinations[0].date", is("2024-01-15")))
                    .andExpect(jsonPath("$.vaccinations[0].nextDate", is("2025-01-15")))
                    .andExpect(jsonPath("$.vaccinations[0].verified", is(true)));
        }

        @Test
        @DisplayName("moderator gets passport - returns 200")
        void get_moderator_returns200() throws Exception {
            createPassport();

            mockMvc.perform(get(PASSPORTS_URL + "/1").header(USER_ID, 20L).header(USER_ROLES, MODERATOR.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)));
        }

        @Test
        @DisplayName("admin gets passport - returns 200")
        void get_admin_returns200() throws Exception {
            createPassport();

            mockMvc.perform(get(PASSPORTS_URL + "/1").header(USER_ID, 20L).header(USER_ROLES, ADMIN.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)));
        }

        @Test
        @DisplayName("different user cannot get passport - returns 403")
        void get_differentUser_returns403() throws Exception {
            createPassport();

            mockMvc.perform(get(PASSPORTS_URL + "/1").header(USER_ID, 20L))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));
        }

        @Test
        @DisplayName("missing passport - returns 404")
        void get_missingPassport_returns404() throws Exception {
            mockMvc.perform(get(PASSPORTS_URL + "/999").header(USER_ID, 20L).header(USER_ROLES, ADMIN.value()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("no user header - returns 401")
        void get_noUserHeader_returns401() throws Exception {
            mockMvc.perform(get(PASSPORTS_URL + "/1")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/passports/{petId}")
    class UpdateTests {
        @Test
        @DisplayName("owner updates passport partially - returns 200")
        void update_ownerPartialUpdate_returns200() throws Exception {
            createPassport();

            mockMvc.perform(put(PASSPORTS_URL + "/1")
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "temperament": "active, friendly, good with kids",
                                        "microchipped": true
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.sellerId", is(10)))
                    .andExpect(jsonPath("$.name", is("Rex")))
                    .andExpect(jsonPath("$.temperament", is("active, friendly, good with kids")))
                    .andExpect(jsonPath("$.microchipped", is(true)))
                    .andExpect(jsonPath("$.neutered", is(true)));
        }

        @Test
        @DisplayName("different seller cannot update passport - returns 403")
        void update_notOwner_returns403() throws Exception {
            createPassport();

            mockMvc.perform(put(PASSPORTS_URL + "/1")
                            .header(USER_ID, 11L)
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"temperament\":\"changed\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));
        }

        @Test
        @DisplayName("no user header - returns 401")
        void update_noUserHeader_returns401() throws Exception {
            mockMvc.perform(put(PASSPORTS_URL + "/1")
                            .header(USER_ROLES, SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"temperament\":\"changed\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    private void createPassport() throws Exception {
        mockMvc.perform(post(PASSPORTS_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated());
    }
}
