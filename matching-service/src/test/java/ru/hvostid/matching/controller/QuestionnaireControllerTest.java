package ru.hvostid.matching.controller;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

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
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class QuestionnaireControllerTest extends AbstractPostgresContainerTest {

    private static final String QUESTIONNAIRE_URL = "/api/v1/match/questionnaire";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE buyer_questionnaire RESTART IDENTITY");
    }

    private String validRequestBody() {
        return """
                {
                    "livingSpace": "APARTMENT",
                    "livingArea": 65,
                    "hasYard": false,
                    "hasChildren": true,
                    "childrenAgeMin": 7,
                    "hasAllergies": false,
                    "petExperience": "BEGINNER",
                    "activityLevel": "MEDIUM",
                    "monthlyBudget": 5000,
                    "workSchedule": "HYBRID",
                    "readyForAdaptation": true,
                    "preferredSpecies": "dog"
                }
                """;
    }

    @Nested
    @DisplayName("POST /api/v1/match/questionnaire")
    class UpsertTests {

        @Test
        @DisplayName("authenticated user creates questionnaire - returns 200")
        void upsert_create_returns200() throws Exception {
            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.userId", is(1)))
                    .andExpect(jsonPath("$.livingSpace", is("APARTMENT")))
                    .andExpect(jsonPath("$.petExperience", is("BEGINNER")))
                    .andExpect(jsonPath("$.preferredSpecies", is("dog")));
        }

        @Test
        @DisplayName("second POST updates existing questionnaire - same id, no duplicate")
        void upsert_secondPost_updatesInPlace() throws Exception {
            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.livingArea", is(65)));

            String updated = """
                    {
                        "livingSpace": "HOUSE",
                        "livingArea": 200,
                        "hasYard": true,
                        "hasChildren": false,
                        "hasAllergies": false,
                        "petExperience": "EXPERIENCED",
                        "activityLevel": "HIGH",
                        "monthlyBudget": 12000,
                        "workSchedule": "HOME",
                        "readyForAdaptation": true
                    }
                    """;

            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updated))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.livingSpace", is("HOUSE")))
                    .andExpect(jsonPath("$.livingArea", is(200)))
                    .andExpect(jsonPath("$.petExperience", is("EXPERIENCED")));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM buyer_questionnaire WHERE user_id = 1", Integer.class);
            assert count != null && count == 1 : "expected exactly 1 row, got " + count;
        }

        @Test
        @DisplayName("missing required field - returns 400")
        void upsert_missingRequiredField_returns400() throws Exception {
            String body = """
                    {
                        "livingSpace": "APARTMENT",
                        "hasYard": false,
                        "hasChildren": false,
                        "hasAllergies": false,
                        "petExperience": "NONE",
                        "activityLevel": "LOW",
                        "monthlyBudget": 1000,
                        "workSchedule": "OFFICE",
                        "readyForAdaptation": false
                    }
                    """;

            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", is(400)))
                    .andExpect(jsonPath("$.message", is("Validation failed")))
                    .andExpect(jsonPath("$.fieldErrors.livingArea", notNullValue()));
        }

        @Test
        @DisplayName("invalid enum value - returns 400")
        void upsert_invalidEnum_returns400() throws Exception {
            String body = validRequestBody().replace("\"APARTMENT\"", "\"CASTLE\"");

            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("negative living area - returns 400")
        void upsert_negativeLivingArea_returns400() throws Exception {
            String body = validRequestBody().replace("\"livingArea\": 65", "\"livingArea\": -10");

            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("no X-User-Id header - returns 401")
        void upsert_noUserId_returns401() throws Exception {
            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/match/questionnaire")
    class GetTests {

        @Test
        @DisplayName("returns own questionnaire after upsert")
        void get_afterUpsert_returnsQuestionnaire() throws Exception {
            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isOk());

            mockMvc.perform(get(QUESTIONNAIRE_URL).header(USER_ID, 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId", is(1)))
                    .andExpect(jsonPath("$.livingSpace", is("APARTMENT")))
                    .andExpect(jsonPath("$.petExperience", is("BEGINNER")));
        }

        @Test
        @DisplayName("user without questionnaire - returns 404")
        void get_noQuestionnaire_returns404() throws Exception {
            mockMvc.perform(get(QUESTIONNAIRE_URL).header(USER_ID, 999L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("no X-User-Id header - returns 401")
        void get_noUserId_returns401() throws Exception {
            mockMvc.perform(get(QUESTIONNAIRE_URL)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("returns only the requesting user's questionnaire, not another user's")
        void get_returnsOnlyOwn() throws Exception {
            mockMvc.perform(post(QUESTIONNAIRE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validRequestBody()))
                    .andExpect(status().isOk());

            mockMvc.perform(get(QUESTIONNAIRE_URL).header(USER_ID, 2L)).andExpect(status().isNotFound());
        }
    }
}
