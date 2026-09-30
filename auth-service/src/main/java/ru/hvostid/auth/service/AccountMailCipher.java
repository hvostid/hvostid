package ru.hvostid.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import ru.hvostid.auth.config.AccountMailProperties;
import tools.jackson.databind.ObjectMapper;

@Component
public class AccountMailCipher {
    private static final byte[] CONTEXT = "hvostid-account-mail-v1".getBytes(StandardCharsets.UTF_8);
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final ObjectMapper mapper;

    public record Message(String email, String path, String token, String subject) {}

    public AccountMailCipher(AccountMailProperties properties, ObjectMapper mapper) {
        this.mapper = mapper;
        this.key = properties.encryptionKey().isBlank()
                ? null
                : new SecretKeySpec(Base64.getDecoder().decode(properties.encryptionKey()), "AES");
    }

    public String encrypt(Message message) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] encrypted = cipher.doFinal(mapper.writeValueAsBytes(message));
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                            .put(nonce)
                            .put(encrypted)
                            .array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Account mail could not be encrypted", ex);
        }
    }

    public Message decrypt(String payload) {
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length < 28) throw new IllegalArgumentException("Invalid encrypted account mail");
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        byte[] nonce = new byte[12];
        byte[] encrypted = new byte[bytes.length - nonce.length];
        buffer.get(nonce).get(encrypted);
        try {
            return mapper.readValue(cipher(Cipher.DECRYPT_MODE, nonce).doFinal(encrypted), Message.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalArgumentException("Invalid encrypted account mail", ex);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        if (key == null) throw new IllegalStateException("Account mail encryption is not configured");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, nonce));
        cipher.updateAAD(CONTEXT);
        return cipher;
    }
}
