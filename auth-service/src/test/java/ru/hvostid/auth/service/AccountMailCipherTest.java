package ru.hvostid.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import ru.hvostid.auth.config.AccountMailProperties;
import tools.jackson.databind.ObjectMapper;

class AccountMailCipherTest {
    @Test
    void mailCannotBeEnabledWithoutItsEncryptionKey() {
        assertThrows(IllegalArgumentException.class, () -> new AccountMailProperties(true, null, null, ""));
        assertDoesNotThrow(() -> new AccountMailProperties(false, null, null, ""));
    }

    @Test
    void randomizedAuthenticatedEncryptionProtectsLinksAndDetectsTampering() {
        var properties =
                new AccountMailProperties(true, null, null, Base64.getEncoder().encodeToString(new byte[32]));
        var cipher = new AccountMailCipher(properties, new ObjectMapper());
        var message =
                new AccountMailCipher.Message("recipient@example.com", "/reset-password", "one-use-secret", "Subject");
        String first = cipher.encrypt(message);
        String second = cipher.encrypt(message);
        assertNotEquals(first, second);
        assertEquals(message, cipher.decrypt(first));
        byte[] corrupted = Base64.getDecoder().decode(first);
        corrupted[corrupted.length - 1] ^= 1;
        assertThrows(
                IllegalArgumentException.class,
                () -> cipher.decrypt(Base64.getEncoder().encodeToString(corrupted)));
    }
}
