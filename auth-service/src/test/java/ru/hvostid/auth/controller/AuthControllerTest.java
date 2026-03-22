package ru.hvostid.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.auth.dto.LoginRequest;
import ru.hvostid.auth.dto.RegisterRequest;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {
    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    // -- Registration --

    @Test
    @DisplayName("POST /register - success - returns 201 with user profile")
    void register_success() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "John Doe");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("user@example.com")))
                .andExpect(jsonPath("$.name", is("John Doe")))
                .andExpect(jsonPath("$.role", is("buyer")));
    }

    @Test
    @DisplayName("POST /register - duplicate email - returns 409")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest("dup@example.com", "password123", "John");

        // First registration
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second registration with same email
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("POST /register - invalid email - returns 400")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("not-an-email", "password123", "John");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - short password - returns 400")
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "short", "John");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /register - blank name - returns 400")
    void register_blankName_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest("user@example.com", "password123", "");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // -- Login --

    @Test
    @DisplayName("POST /login - success - returns 200 with tokens")
    void login_success() throws Exception {
        // Register first
        RegisterRequest regRequest = new RegisterRequest("login@example.com", "password123", "Jane");
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login
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
    @DisplayName("POST /login - wrong password - returns 401")
    void login_wrongPassword_returns401() throws Exception {
        // Register first
        RegisterRequest regRequest = new RegisterRequest("wrong@example.com", "password123", "Test");
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login with wrong password
        LoginRequest loginRequest = new LoginRequest("wrong@example.com", "bad_password");
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("POST /login - non-existent user - returns 401")
    void login_noSuchUser_returns401() throws Exception {
        LoginRequest loginRequest = new LoginRequest("ghost@example.com", "password123");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /login - tokens are opaque (not JWT)")
    void login_tokensAreNotJwt() throws Exception {
        // Register
        RegisterRequest regRequest = new RegisterRequest("opaque@example.com", "password123", "Test");
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated());

        // Login and check token format
        LoginRequest loginRequest = new LoginRequest("opaque@example.com", "password123");
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                // JWT tokens have format xxx.xxx.xxx (2 dots). Opaque tokens must not match.
                .andExpect(jsonPath("$.accessToken").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.matchesPattern("^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$"))
                ));
    }
}
