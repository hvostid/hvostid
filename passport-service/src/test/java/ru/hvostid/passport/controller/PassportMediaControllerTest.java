package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.ProxyHeaders.REFERRER_POLICY;
import static ru.hvostid.common.http.ProxyHeaders.X_ACCEL_REDIRECT;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
import static ru.hvostid.common.security.UserRole.SELLER;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PassportMediaControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";
    private static final String DOCS_URL = PASSPORTS_URL + "/1/docs";
    private static final String CONTENT_URL = DOCS_URL + "/1/content";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE passport_documents, vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("valid ticket - returns 204 with X-Accel-Redirect")
    void stream_validTicket_returns204WithXAccelRedirect() throws Exception {
        String ticket = issueTicket();

        mockMvc.perform(get(CONTENT_URL).param("t", ticket))
                .andExpect(status().isNoContent())
                .andExpect(header().string(X_ACCEL_REDIRECT, startsWith("/_protected/minio/pet-photos/")))
                .andExpect(header().string(X_ACCEL_REDIRECT, containsString("X-Amz-Signature=")))
                .andExpect(header().string(REFERRER_POLICY, is("no-referrer")));
    }

    @Test
    @DisplayName("ticket replay - second request returns 404")
    void stream_replayedTicket_returns404() throws Exception {
        String ticket = issueTicket();

        mockMvc.perform(get(CONTENT_URL).param("t", ticket)).andExpect(status().isNoContent());
        mockMvc.perform(get(CONTENT_URL).param("t", ticket)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("unknown ticket - returns 404")
    void stream_unknownTicket_returns404() throws Exception {
        createPassport();
        uploadPhoto();

        mockMvc.perform(get(CONTENT_URL).param("t", "definitely-not-a-real-ticket"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ticket bound to a different document - returns 404")
    void stream_mismatchedDocId_returns404() throws Exception {
        String ticket = issueTicket();

        // Ticket was issued for docId=1; redeeming against docId=2 path must fail.
        mockMvc.perform(get(DOCS_URL + "/2/content").param("t", ticket)).andExpect(status().isNotFound());
    }

    private String issueTicket() throws Exception {
        createPassport();
        uploadPhoto();
        MvcResult result = mockMvc.perform(
                        get(DOCS_URL + "/1").header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", startsWith("/api/v1/passports/1/docs/1/content?t=")))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String url = body.get("url").asString();
        return url.substring(url.indexOf("?t=") + 3);
    }

    private void createPassport() throws Exception {
        mockMvc.perform(post(PASSPORTS_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "species": "dog",
                                    "breed": "Husky",
                                    "name": "Rex",
                                    "birthDate": "2023-05-10",
                                    "gender": "MALE",
                                    "color": "grey-white",
                                    "temperament": "active, friendly",
                                    "neutered": true,
                                    "microchipped": false
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private void uploadPhoto() throws Exception {
        mockMvc.perform(multipart(DOCS_URL)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", "image".getBytes()))
                        .param("type", "PHOTO")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value()))
                .andExpect(status().isCreated());
    }
}
