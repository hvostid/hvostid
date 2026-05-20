package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.ProxyHeaders.REFERRER_POLICY;
import static ru.hvostid.common.http.ProxyHeaders.X_ACCEL_REDIRECT;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
import static ru.hvostid.common.security.UserRole.SELLER;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import ru.hvostid.passport.client.ListingServiceClient;

@SpringBootTest
@AutoConfigureMockMvc
class PassportCoverControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";
    private static final String DOCS_URL = PASSPORTS_URL + "/1/docs";
    private static final String COVER_URL = PASSPORTS_URL + "/1/cover";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingServiceClient listingServiceClient;

    @BeforeEach
    void defaultListingMock() {
        when(listingServiceClient.hasPublishedListingForPassport(any(), any())).thenReturn(true);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE passport_documents, vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("PUBLISHED listing with PHOTO - returns 204 + X-Accel-Redirect to first photo")
    void cover_publishedWithPhoto_returns204() throws Exception {
        createPassport();
        uploadPhoto("first.jpg");
        uploadPhoto("second.jpg");

        // No Bearer token -- this is the public catalog path.
        mockMvc.perform(get(COVER_URL))
                .andExpect(status().isNoContent())
                .andExpect(header().string(X_ACCEL_REDIRECT, startsWith("/_protected/minio/pet-photos/")))
                .andExpect(header().string(X_ACCEL_REDIRECT, containsString("X-Amz-Signature=")))
                .andExpect(header().string(REFERRER_POLICY, is("no-referrer")));
    }

    @Test
    @DisplayName("no PUBLISHED listing reference - returns 404")
    void cover_noPublishedListing_returns404() throws Exception {
        createPassport();
        uploadPhoto("first.jpg");
        when(listingServiceClient.hasPublishedListingForPassport(any(), any())).thenReturn(false);

        mockMvc.perform(get(COVER_URL)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUBLISHED listing but no PHOTO uploaded - returns 404")
    void cover_publishedNoPhoto_returns404() throws Exception {
        createPassport();
        // No upload.

        mockMvc.perform(get(COVER_URL)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("passport does not exist - returns 404")
    void cover_unknownPassport_returns404() throws Exception {
        mockMvc.perform(get(COVER_URL)).andExpect(status().isNotFound());
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

    private void uploadPhoto(String filename) throws Exception {
        mockMvc.perform(multipart(DOCS_URL)
                        .file(new MockMultipartFile("file", filename, "image/jpeg", "image".getBytes()))
                        .param("type", "PHOTO")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value()))
                .andExpect(status().isCreated());
    }
}
