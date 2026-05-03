package ru.hvostid.auth.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Generates cryptographically secure opaque tokens.
 */
@Service
public class TokenService {
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a random opaque token encoded as URL-safe Base64.
     *
     * @return 32-byte random token as a Base64 string
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
