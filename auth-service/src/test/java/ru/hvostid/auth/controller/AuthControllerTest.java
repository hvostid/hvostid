package ru.hvostid.auth.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import ru.hvostid.auth.dto.LoginRequest;
import ru.hvostid.auth.dto.RegisterRequest;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractPostgresContainerTest {
    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";
    private static final String INTROSPECT_URL = "/internal/auth/introspect";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE sessions, user_roles, users RESTART IDENTITY CASCADE");
    }

    // -- Registration --

    private void registerUser(String email, String password, String name) throws Exception {
        RegisterRequest request = new RegisterRequest(email, password, name);
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // -- Login --

    private JsonNode loginAndGetTokens(String email, String password, String name) throws Exception {
        registerUser(email, password, name);

        LoginRequest loginRequest = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // -- Introspect --

    private String loginAndGetAccessToken(String email, String password, String name) throws Exception {
        JsonNode tokens = loginAndGetTokens(email, password, name);
        return tokens.get("accessToken").asString();
    }

    // -- Refresh --

    private String loginAndGetRefreshToken(String email, String password, String name) throws Exception {
        JsonNode tokens = loginAndGetTokens(email, password, name);
        return tokens.get("refreshToken").asString();
    }

    // -- Registration --

    @Nested
    @DisplayName("POST /register")
    class RegisterTests {
        @Test
        @DisplayName("success - returns 201 with user profile")
        void register_success() throws Exception {
            RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John Doe");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.email", is("user@example.com")))
                    .andExpect(jsonPath("$.name", is("John Doe")))
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())));
        }

        @Test
        @DisplayName("duplicate email - returns 409")
        void register_duplicateEmail_returns409() throws Exception {
            RegisterRequest request = new RegisterRequest("dup@example.com", "password123", "John");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status", is(409)));
        }

        @Test
        @DisplayName("invalid email - returns 400")
        void register_invalidEmail_returns400() throws Exception {
            RegisterRequest request = new RegisterRequest("not-an-email", "password123", "John");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("short password - returns 400")
        void register_shortPassword_returns400() throws Exception {
            RegisterRequest request = new RegisterRequest("user@example.com", "short", "John");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("blank name - returns 400")
        void register_blankName_returns400() throws Exception {
            RegisterRequest request = new RegisterRequest("user@example.com", "password123", "");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -- Login --

    @Nested
    @DisplayName("POST /login")
    class LoginTests {
        @Test
        @DisplayName("success - returns 200 with tokens")
        void login_success() throws Exception {
            registerUser("login@example.com", "password123", "Jane");

            LoginRequest loginRequest = new LoginRequest("login@example.com", "password123");
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.expiresIn", is(1800)));
        }

        @Test
        @DisplayName("wrong password - returns 401")
        void login_wrongPassword_returns401() throws Exception {
            registerUser("wrong@example.com", "password123", "Test");

            LoginRequest loginRequest = new LoginRequest("wrong@example.com", "bad_password");
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status", is(401)));
        }

        @Test
        @DisplayName("non-existent user - returns 401")
        void login_noSuchUser_returns401() throws Exception {
            LoginRequest loginRequest = new LoginRequest("ghost@example.com", "password123");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("tokens are opaque (not JWT)")
        void login_tokensAreNotJwt() throws Exception {
            registerUser("opaque@example.com", "password123", "Test");

            LoginRequest loginRequest = new LoginRequest("opaque@example.com", "password123");
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken")
                            .value(not(matchesPattern("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"))));
        }
    }

    // -- Introspect --

    @Nested
    @DisplayName("POST /internal/auth/introspect")
    class IntrospectTests {
        @Test
        @DisplayName("valid token - returns active: true with userId and roles")
        void introspect_validToken_returnsActive() throws Exception {
            String accessToken = loginAndGetAccessToken("intro@example.com", "password123", "Intro");

            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + accessToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(true)))
                    .andExpect(jsonPath("$.userId", notNullValue()))
                    .andExpect(jsonPath("$.roles", hasItem(UserRole.BUYER.value())));
        }

        @Test
        @DisplayName("non-existent token - returns active: false")
        void introspect_unknownToken_returnsInactive() throws Exception {
            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"nonexistent_token_value\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(false)))
                    .andExpect(jsonPath("$.userId").doesNotExist())
                    .andExpect(jsonPath("$.roles").doesNotExist());
        }

        @Test
        @DisplayName("blank token - returns 400")
        void introspect_blankToken_returns400() throws Exception {
            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // -- Refresh --

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTests {
        @Test
        @DisplayName("valid refresh token - returns new token pair")
        void refresh_validToken_returnsNewPair() throws Exception {
            String refreshToken = loginAndGetRefreshToken("refresh@example.com", "password123", "Refresh");

            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()))
                    .andExpect(jsonPath("$.refreshToken", notNullValue()))
                    .andExpect(jsonPath("$.expiresIn", is(1800)));
        }

        @Test
        @DisplayName("refresh invalidates old token pair")
        void refresh_invalidatesOldTokens() throws Exception {
            JsonNode tokens = loginAndGetTokens("oldtoken@example.com", "password123", "Old");

            String oldAccessToken = tokens.get("accessToken").asString();
            String oldRefreshToken = tokens.get("refreshToken").asString();

            // Refresh to get new tokens
            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                    .andExpect(status().isOk());

            // Old access token should now be inactive
            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + oldAccessToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(false)));

            // Old refresh token should now be invalid
            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + oldRefreshToken + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("new tokens after refresh are valid")
        void refresh_newTokensAreValid() throws Exception {
            String refreshToken = loginAndGetRefreshToken("newvalid@example.com", "password123", "Valid");

            MvcResult result = mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            String newAccessToken = body.get("accessToken").asString();

            // New access token should be active
            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + newAccessToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(true)));
        }

        @Test
        @DisplayName("invalid refresh token - returns 401")
        void refresh_invalidToken_returns401() throws Exception {
            mockMvc.perform(post(REFRESH_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"invalid_token_value\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -- Logout --

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutTests {
        @Test
        @DisplayName("valid token - returns 204")
        void logout_validToken_returns204() throws Exception {
            String accessToken = loginAndGetAccessToken("logout@example.com", "password123", "Logout");

            mockMvc.perform(post(LOGOUT_URL).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("after logout introspect returns active: false")
        void logout_thenIntrospect_returnsInactive() throws Exception {
            String accessToken = loginAndGetAccessToken("logoutcheck@example.com", "password123", "Check");

            // Logout
            mockMvc.perform(post(LOGOUT_URL).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            // Introspect should return inactive
            mockMvc.perform(post(INTROSPECT_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + accessToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active", is(false)));
        }

        @Test
        @DisplayName("double logout - still returns 204")
        void logout_twice_returns204() throws Exception {
            String accessToken = loginAndGetAccessToken("double@example.com", "password123", "Double");

            mockMvc.perform(post(LOGOUT_URL).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());

            mockMvc.perform(post(LOGOUT_URL).header("Authorization", "Bearer " + accessToken))
                    .andExpect(status().isNoContent());
        }
    }
}
