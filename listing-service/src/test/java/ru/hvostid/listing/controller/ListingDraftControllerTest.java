package ru.hvostid.listing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.listing.ListingIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingDraftControllerTest extends ListingIntegrationTest {
    private static final String DRAFT_URL = "/api/v1/listings/draft";
    private static final String FORM = """
            {"title":"Friendly kitten","description":"A playful kitten",
             "species":"CAT","breed":"Siamese","age":0,"price":0,"city":"Moscow"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void savesReplacesAndRestoresAllFieldsWithoutCreatingAListing() throws Exception {
        save(FORM);
        save(FORM.replace("Moscow", "Novosibirsk"));
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(content().json(FORM.replace("Moscow", "Novosibirsk")));
        mockMvc.perform(get("/api/v1/listings/my").header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sellersCannotReadOrOverwriteEachOthersDrafts() throws Exception {
        save(FORM);
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 11L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNoContent());
        mockMvc.perform(put(DRAFT_URL)
                        .header(USER_ID, 11L)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORM.replace("Moscow", "Omsk")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(content().json(FORM));
    }

    @Test
    void requiresSellerAuthenticationAndValidFields() throws Exception {
        mockMvc.perform(get(DRAFT_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.BUYER.value()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(DRAFT_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORM))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(DRAFT_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORM.replace("Friendly kitten", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void failedCreationPreservesDraftAndSuccessfulCreationConsumesIt() throws Exception {
        save(FORM);
        mockMvc.perform(post("/api/v1/listings")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORM))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(content().json(FORM));
        mockMvc.perform(post("/api/v1/listings")
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FORM.replace("}", ",\"passportId\":\"42\"}")))
                .andExpect(status().isCreated());
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNoContent());
    }

    @Test
    void optionalValuesCanBeCleared() throws Exception {
        save(FORM);
        String minimal = """
                {"title":"Friendly kitten","species":"CAT","city":"Moscow"}
                """;
        save(minimal);
        mockMvc.perform(get(DRAFT_URL).header(USER_ID, 10L).header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.age").doesNotExist())
                .andExpect(jsonPath("$.price").doesNotExist())
                .andExpect(jsonPath("$.breed").doesNotExist());
    }

    private void save(String form) throws Exception {
        mockMvc.perform(put(DRAFT_URL)
                        .header(USER_ID, 10L)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(form))
                .andExpect(status().isNoContent());
    }
}
