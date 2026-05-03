package ru.hvostid.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService tokenService = new TokenService();

    @Test
    @DisplayName("generateToken - returns non-null Base64 string of 32 bytes")
    void generateToken_returnsValidBase64() {
        String token = tokenService.generateToken();

        assertNotNull(token);
        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertEquals(32, decoded.length);
    }

    @Test
    @DisplayName("generateToken - consecutive calls produce unique tokens")
    void generateToken_producesUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(tokenService.generateToken());
        }
        assertEquals(100, tokens.size());
    }

    @Test
    @DisplayName("generateToken - token is not a JWT (no dots)")
    void generateToken_isNotJwt() {
        String token = tokenService.generateToken();
        // JWT tokens have exactly 2 dots separating 3 parts
        assertNotEquals(2, token.chars().filter(c -> c == '.').count(), "Token must not look like a JWT");
    }
}
