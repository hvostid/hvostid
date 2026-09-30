package ru.hvostid.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hvostid.auth.mail")
public record AccountMailProperties(boolean enabled, String from, String publicBaseUrl, String encryptionKey) {
    public AccountMailProperties {
        if (from == null || from.isBlank()) from = "no-reply@localhost";
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) publicBaseUrl = "http://localhost";
        if (!publicBaseUrl.matches("https?://[^\\s?#]+")) {
            throw new IllegalArgumentException(
                    "AUTH_PUBLIC_BASE_URL must be an absolute HTTP(S) URL without query or fragment");
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        encryptionKey = encryptionKey == null ? "" : encryptionKey.strip();
        if (!encryptionKey.isBlank() || enabled) {
            try {
                if (java.util.Base64.getDecoder().decode(encryptionKey).length != 32)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "AUTH_MAIL_ENCRYPTION_KEY must contain a Base64-encoded 32-byte key when account mail is enabled");
            }
        }
    }
}
