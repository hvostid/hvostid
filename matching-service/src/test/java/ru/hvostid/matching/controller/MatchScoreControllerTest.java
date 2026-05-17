package ru.hvostid.matching.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.matching.domain.CompatibilityLevel;
import ru.hvostid.matching.dto.FactorScoreDto;
import ru.hvostid.matching.dto.MatchScoreResponse;
import ru.hvostid.matching.exception.QuestionnaireNotFoundException;
import ru.hvostid.matching.service.MatchScoreService;

@SpringBootTest
@AutoConfigureMockMvc
class MatchScoreControllerTest extends AbstractPostgresContainerTest {
    private static final String SCORE_URL = "/api/v1/match/score";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchScoreService matchScoreService;

    @Nested
    @DisplayName("POST /api/v1/match/score")
    class CalculateScoreTests {
        @Test
        @DisplayName("returns 200 with score breakdown")
        void calculate_returns200() throws Exception {
            MatchScoreResponse response = new MatchScoreResponse(
                    78,
                    CompatibilityLevel.GOOD,
                    List.of(new FactorScoreDto("living_space", 18, 20, "Apartment is suitable")),
                    false);
            when(matchScoreService.calculateScore(eq(42L), eq(1L), any())).thenReturn(response);

            mockMvc.perform(post(SCORE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"listingId\": 42}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.score", is(78)))
                    .andExpect(jsonPath("$.level", is("GOOD")))
                    .andExpect(jsonPath("$.degraded", is(false)))
                    .andExpect(jsonPath("$.factors", hasSize(1)))
                    .andExpect(jsonPath("$.factors[0].name", is("living_space")));
        }

        @Test
        @DisplayName("missing listingId returns 400")
        void calculate_missingListingId_returns400() throws Exception {
            mockMvc.perform(post(SCORE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("no questionnaire returns 404")
        void calculate_noQuestionnaire_returns404() throws Exception {
            when(matchScoreService.calculateScore(anyLong(), eq(1L), any()))
                    .thenThrow(new QuestionnaireNotFoundException("Questionnaire not found for user: 1"));

            mockMvc.perform(post(SCORE_URL)
                            .header(USER_ID, 1L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"listingId\": 1}"))
                    .andExpect(status().isNotFound());
        }
    }
}
