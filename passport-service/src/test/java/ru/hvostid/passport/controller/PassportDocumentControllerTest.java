package ru.hvostid.passport.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;
import static ru.hvostid.common.security.UserRole.ADMIN;
import static ru.hvostid.common.security.UserRole.BUYER;
import static ru.hvostid.common.security.UserRole.MODERATOR;
import static ru.hvostid.common.security.UserRole.SELLER;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.exception.ListingServiceUnavailableException;
import ru.hvostid.passport.service.PassportDocumentValidator;

@SpringBootTest
@AutoConfigureMockMvc
class PassportDocumentControllerTest extends AbstractPassportIntegrationTest {
    private static final String PASSPORTS_URL = "/api/v1/passports";
    private static final String DOCS_URL = PASSPORTS_URL + "/1/docs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ListingServiceClient listingServiceClient;

    @BeforeEach
    void resetListingClient() {
        // Default to "no PUBLISHED listing" so non-privileged callers in legacy
        // tests get the same 404 they used to get as 403 before this change.
        when(listingServiceClient.hasPublishedListingForPassport(any(), any())).thenReturn(false);
    }

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE passport_documents, vaccinations, pet_passports RESTART IDENTITY CASCADE");
    }

    @Nested
    @DisplayName("POST /api/v1/passports/{id}/docs")
    class UploadTests {
        @Test
        @DisplayName("owner uploads photo - returns 201")
        void upload_ownerPhoto_returns201() throws Exception {
            createPassport();

            mockMvc.perform(multipart(DOCS_URL)
                            .file(file("photo.jpg", "image/jpeg", "image".getBytes()))
                            .param("type", "PHOTO")
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.type", is("PHOTO")))
                    .andExpect(jsonPath("$.originalFilename", is("photo.jpg")))
                    .andExpect(jsonPath("$.mimeType", is("image/jpeg")))
                    .andExpect(jsonPath("$.size", is(5)))
                    .andExpect(jsonPath("$.uploadedAt", notNullValue()));
        }

        @Test
        @DisplayName("owner uploads pdf - returns 201")
        void upload_ownerPdf_returns201() throws Exception {
            createPassport();

            mockMvc.perform(multipart(DOCS_URL)
                            .file(file("record.pdf", "application/pdf", "pdf".getBytes()))
                            .param("type", "VET_RECORD")
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.type", is("VET_RECORD")))
                    .andExpect(jsonPath("$.originalFilename", is("record.pdf")));
        }

        @Test
        @DisplayName("non-owner cannot upload - returns 403")
        void upload_nonOwner_returns403() throws Exception {
            createPassport();

            mockMvc.perform(multipart(DOCS_URL)
                            .file(file("photo.jpg", "image/jpeg", "image".getBytes()))
                            .param("type", "PHOTO")
                            .header(USER_ID, 11L)
                            .header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("too large file - returns 413")
        void upload_tooLarge_returns413() throws Exception {
            createPassport();
            byte[] content = new byte[(int) PassportDocumentValidator.MAX_FILE_SIZE_BYTES + 1];

            mockMvc.perform(multipart(DOCS_URL)
                            .file(file("photo.jpg", "image/jpeg", content))
                            .param("type", "PHOTO")
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isContentTooLarge())
                    .andExpect(jsonPath("$.status", is(413)));
        }

        @Test
        @DisplayName("unsupported extension - returns 415")
        void upload_unsupportedExtension_returns415() throws Exception {
            createPassport();

            mockMvc.perform(multipart(DOCS_URL)
                            .file(file("archive.zip", "application/pdf", "zip".getBytes()))
                            .param("type", "OTHER")
                            .header(USER_ID, 10L)
                            .header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.status", is(415)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/passports/{id}/docs")
    class ListTests {
        @Test
        @DisplayName("owner lists documents - returns metadata with downloadUrl")
        void list_owner_returnsDocuments() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type", is("PHOTO")))
                    .andExpect(jsonPath("$[0].originalFilename", is("photo.jpg")))
                    .andExpect(jsonPath("$[0].downloadUrl", containsString("X-Amz-Expires=600")));
        }

        @Test
        @DisplayName("moderator lists documents - returns metadata")
        void list_moderator_returnsDocuments() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 20L).header(USER_ROLES, MODERATOR.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].downloadUrl", notNullValue()));
        }

        @Test
        @DisplayName("buyer on PUBLISHED listing sees only PHOTOs - returns 200")
        void list_buyerPublishedListing_returnsOnlyPhotos() throws Exception {
            createPassport();
            uploadPhoto();
            uploadVetRecord();
            when(listingServiceClient.hasPublishedListingForPassport(eq(1L), any()))
                    .thenReturn(true);

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].type", is("PHOTO")))
                    .andExpect(jsonPath("$[0].downloadUrl", notNullValue()));
        }

        @Test
        @DisplayName("buyer without PUBLISHED listing - returns 404")
        void list_buyerDraftListing_returns404() throws Exception {
            createPassport();
            uploadPhoto();
            // default mock: hasPublishedListingForPassport returns false

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("roleless caller without PUBLISHED listing - returns 404")
        void list_rolelessCaller_returns404() throws Exception {
            createPassport();

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 20L)).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("listing-service unavailable - returns 503")
        void list_listingServiceUnavailable_returns503() throws Exception {
            createPassport();
            uploadPhoto();
            when(listingServiceClient.hasPublishedListingForPassport(any(), any()))
                    .thenThrow(new ListingServiceUnavailableException("upstream down"));

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/passports/{id}/docs/{docId}")
    class DownloadTests {
        @Test
        @DisplayName("owner downloads document - returns 302")
        void download_owner_returns302() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(get(DOCS_URL + "/1").header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("X-Amz-Expires=600")));
        }

        @Test
        @DisplayName("admin downloads document - returns 302")
        void download_admin_returns302() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(get(DOCS_URL + "/1").header(USER_ID, 20L).header(USER_ROLES, ADMIN.value()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("X-Amz-Expires=600")));
        }

        @Test
        @DisplayName("buyer on PUBLISHED listing downloads PHOTO - returns 302")
        void download_buyerPublishedListingPhoto_returns302() throws Exception {
            createPassport();
            uploadPhoto();
            when(listingServiceClient.hasPublishedListingForPassport(eq(1L), any()))
                    .thenReturn(true);

            mockMvc.perform(get(DOCS_URL + "/1").header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION, containsString("X-Amz-Expires=600")));
        }

        @Test
        @DisplayName("buyer on PUBLISHED listing downloads VET_RECORD - returns 404")
        void download_buyerPublishedListingVetRecord_returns404() throws Exception {
            createPassport();
            uploadVetRecord();
            when(listingServiceClient.hasPublishedListingForPassport(any(), any()))
                    .thenReturn(true);

            mockMvc.perform(get(DOCS_URL + "/1").header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("buyer without PUBLISHED listing - returns 404")
        void download_buyerDraftListing_returns404() throws Exception {
            createPassport();
            uploadPhoto();
            // default mock: hasPublishedListingForPassport returns false

            mockMvc.perform(get(DOCS_URL + "/1").header(USER_ID, 99L).header(USER_ROLES, BUYER.value()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/passports/{id}/docs/{docId}")
    class DeleteTests {
        @Test
        @DisplayName("owner deletes document - returns 204")
        void delete_owner_returns204() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(delete(DOCS_URL + "/1").header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(DOCS_URL).header(USER_ID, 10L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("non-owner cannot delete document - returns 403")
        void delete_nonOwner_returns403() throws Exception {
            createPassport();
            uploadPhoto();

            mockMvc.perform(delete(DOCS_URL + "/1").header(USER_ID, 11L).header(USER_ROLES, SELLER.value()))
                    .andExpect(status().isForbidden());
        }
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
                        .file(file("photo.jpg", "image/jpeg", "image".getBytes()))
                        .param("type", "PHOTO")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value()))
                .andExpect(status().isCreated());
    }

    private void uploadVetRecord() throws Exception {
        mockMvc.perform(multipart(DOCS_URL)
                        .file(file("record.pdf", "application/pdf", "pdf".getBytes()))
                        .param("type", "VET_RECORD")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, SELLER.value()))
                .andExpect(status().isCreated());
    }

    private MockMultipartFile file(String filename, String contentType, byte[] content) {
        return new MockMultipartFile("file", filename, contentType, content);
    }
}
