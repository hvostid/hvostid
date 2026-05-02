package ru.hvostid.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.hvostid.auth.dto.RegisterRequest;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProfileControllerTest extends AbstractPostgresContainerTest {
    private static final String PROFILE_ME_URL = "/api/v1/profile/me";
    private static final String PROFILE_ROLES_URL = "/api/v1/profile/me/roles";
    private static final String REGISTER_URL = "/api/v1/auth/register";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Register a user and return their ID.
     */
    private Long registerAndGetUserId(String email, String name) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123", name);
        MvcResult result = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    // -- GET /api/v1/profile/me --

    @Nested
    @DisplayName("GET /api/v1/profile/me")
    class GetProfileTests {
        @Test
        @DisplayName("with authenticated user id - returns 200 with profile")
        void getProfile_validUserId_returnsProfile() throws Exception {
            Long userId = registerAndGetUserId("profile@example.com", "Profile User");

            mockMvc.perform(get(PROFILE_ME_URL)
                            .header(USER_ID, userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(userId.intValue())))
                    .andExpect(jsonPath("$.email", is("profile@example.com")))
                    .andExpect(jsonPath("$.name", is("Profile User")))
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())))
                    .andExpect(jsonPath("$.roles", hasSize(1)));
        }

        @Test
        @DisplayName("without authenticated user - returns 401")
        void getProfile_noUserIdHeader_returns401() throws Exception {
            mockMvc.perform(get(PROFILE_ME_URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("with non-existent user id - returns 404")
        void getProfile_nonExistentUser_returns404() throws Exception {
            mockMvc.perform(get(PROFILE_ME_URL)
                            .header(USER_ID, 99999))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status", is(404)));
        }

        @Test
        @DisplayName("profile has null optional fields by default")
        void getProfile_defaultNullFields() throws Exception {
            Long userId = registerAndGetUserId("defaults@example.com", "Defaults");

            mockMvc.perform(get(PROFILE_ME_URL)
                            .header(USER_ID, userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.phone").doesNotExist())
                    .andExpect(jsonPath("$.city").doesNotExist())
                    .andExpect(jsonPath("$.bio").doesNotExist())
                    .andExpect(jsonPath("$.rating").doesNotExist());
        }
    }

    // -- PUT /api/v1/profile/me --

    @Nested
    @DisplayName("PUT /api/v1/profile/me")
    class UpdateProfileTests {
        @Test
        @DisplayName("update all fields - returns 200 with updated profile")
        void updateProfile_allFields_returnsUpdated() throws Exception {
            Long userId = registerAndGetUserId("update@example.com", "Old Name");

            String body = """
                    {
                        "name": "New Name",
                        "phone": "+79001234567",
                        "city": "Moscow",
                        "bio": "My bio text"
                    }
                    """;

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("New Name")))
                    .andExpect(jsonPath("$.phone", is("+79001234567")))
                    .andExpect(jsonPath("$.city", is("Moscow")))
                    .andExpect(jsonPath("$.bio", is("My bio text")))
                    .andExpect(jsonPath("$.email", is("update@example.com")));
        }

        @Test
        @DisplayName("update only name - other fields unchanged")
        void updateProfile_onlyName_returnsUpdated() throws Exception {
            Long userId = registerAndGetUserId("partial@example.com", "Old Name");

            // First set phone
            String firstUpdate = """
                    { "phone": "+79001234567" }
                    """;
            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstUpdate))
                    .andExpect(status().isOk());

            // Then update only name
            String secondUpdate = """
                    { "name": "New Name" }
                    """;
            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(secondUpdate))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("New Name")))
                    .andExpect(jsonPath("$.phone", is("+79001234567")));
        }

        @Test
        @DisplayName("without authenticated user - returns 401")
        void updateProfile_noUserIdHeader_returns401() throws Exception {
            String body = """
                    { "name": "Name" }
                    """;

            mockMvc.perform(put(PROFILE_ME_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("non-existent user - returns 404")
        void updateProfile_nonExistentUser_returns404() throws Exception {
            String body = """
                    { "name": "Name" }
                    """;

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, 99999)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("name too long - returns 400")
        void updateProfile_nameTooLong_returns400() throws Exception {
            Long userId = registerAndGetUserId("toolong@example.com", "Normal");

            String longName = "A".repeat(256);
            String body = "{\"name\": \"" + longName + "\"}";

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("bio too long - returns 400")
        void updateProfile_bioTooLong_returns400() throws Exception {
            Long userId = registerAndGetUserId("longbio@example.com", "Normal");

            String longBio = "B".repeat(2001);
            String body = "{\"bio\": \"" + longBio + "\"}";

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("empty name string - returns 400")
        void updateProfile_emptyName_returns400() throws Exception {
            Long userId = registerAndGetUserId("empty@example.com", "Normal");

            String body = """
                    { "name": "" }
                    """;

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("update does not change roles")
        void updateProfile_doesNotChangeRoles() throws Exception {
            Long userId = registerAndGetUserId("roles@example.com", "Test");

            String body = """
                    { "name": "Updated" }
                    """;

            mockMvc.perform(put(PROFILE_ME_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())))
                    .andExpect(jsonPath("$.roles", hasSize(1)));
        }
    }

    // -- POST /api/v1/profile/me/roles --

    @Nested
    @DisplayName("POST /api/v1/profile/me/roles")
    class AddRoleTests {
        @Test
        @DisplayName("add SELLER role - returns 200 with updated roles")
        void addRole_seller_returnsUpdatedRoles() throws Exception {
            Long userId = registerAndGetUserId("seller@example.com", "Seller");

            String body = """
                    { "role": "SELLER" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())))
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.SELLER.value())))
                    .andExpect(jsonPath("$.roles", hasSize(2)));
        }

        @Test
        @DisplayName("add SELLER role persists across requests")
        void addRole_seller_persistsOnGetProfile() throws Exception {
            Long userId = registerAndGetUserId("persist@example.com", "Persist");

            String body = """
                    { "role": "SELLER" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            // Verify via GET profile
            mockMvc.perform(get(PROFILE_ME_URL)
                            .header(USER_ID, userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.SELLER.value())))
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())));
        }

        @Test
        @DisplayName("add MODERATOR role - returns 403")
        void addRole_moderator_returns403() throws Exception {
            Long userId = registerAndGetUserId("mod@example.com", "Mod");

            String body = """
                    { "role": "MODERATOR" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));
        }

        @Test
        @DisplayName("add ADMIN role - returns 403")
        void addRole_admin_returns403() throws Exception {
            Long userId = registerAndGetUserId("adm@example.com", "Admin");

            String body = """
                    { "role": "ADMIN" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status", is(403)));
        }

        @Test
        @DisplayName("add unknown role - returns 403")
        void addRole_unknownRole_returns403() throws Exception {
            Long userId = registerAndGetUserId("unknown@example.com", "Unknown");

            String body = """
                    { "role": "SUPERADMIN" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("blank role - returns 400")
        void addRole_blankRole_returns400() throws Exception {
            Long userId = registerAndGetUserId("blank@example.com", "Blank");

            String body = """
                    { "role": "" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("without authenticated user - returns 401")
        void addRole_noUserIdHeader_returns401() throws Exception {
            String body = """
                    { "role": "SELLER" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("non-existent user - returns 404")
        void addRole_nonExistentUser_returns404() throws Exception {
            String body = """
                    { "role": "SELLER" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, 99999)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("add SELLER twice - idempotent, returns 200")
        void addRole_sellerTwice_idempotent() throws Exception {
            Long userId = registerAndGetUserId("twice@example.com", "Twice");

            String body = """
                    { "role": "SELLER" }
                    """;

            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());

            // Adding again should still succeed
            mockMvc.perform(post(PROFILE_ROLES_URL)
                            .header(USER_ID, userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles", hasSize(2)));
        }
    }
}
