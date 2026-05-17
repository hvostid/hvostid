package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
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
class InternalPassportControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";
    private static final String INTERNAL_PASSPORTS_URL = "/internal/passports";

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

    private long createPassport() throws Exception {
        mockMvc.perform(post(PASSPORTS_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestBody()))
                .andExpect(status().isCreated());
        return 1L;
    }

    @Nested
    @DisplayName("GET /internal/passports/{petId}")
    class GetInternalTests {
        @Test
        @DisplayName("returns passport without authentication headers")
        void getInternal_existing_returns200() throws Exception {
            long passportId = createPassport();

            mockMvc.perform(get(INTERNAL_PASSPORTS_URL + "/" + passportId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.breed", is("Husky")))
                    .andExpect(jsonPath("$.temperament", is("active, friendly")))
                    .andExpect(jsonPath("$.createdAt", notNullValue()));
        }

        @Test
        @DisplayName("unknown passport - returns 404")
        void getInternal_notFound_returns404() throws Exception {
            mockMvc.perform(get(INTERNAL_PASSPORTS_URL + "/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }
    }
}
