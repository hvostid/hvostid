package ru.hvostid.passport.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.hvostid.passport.config.MediaTicketProperties;
import ru.hvostid.passport.entity.MediaTicket;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Issues and redeems single use media tickets.
 *
 * <p>The ticket value itself is 32 cryptographically random bytes encoded as
 * URL safe base64 (43 chars). The Redis value is the JSON serialised
 * {@link MediaTicket} record, keyed under {@value KEY_PREFIX} with an
 * expiry equal to {@link MediaTicketProperties#ticketTtl()}.
 *
 * <p>Redemption is atomic via GETDEL: the first reader wins and any retry
 * sees the ticket as missing. This bounds the blast radius if a ticketed URL
 * leaks: at most one extra fetch.
 */
@Service
public class MediaTicketService {
    private static final Logger log = LoggerFactory.getLogger(MediaTicketService.class);
    private static final String KEY_PREFIX = "media-ticket:";
    private static final int TICKET_BYTES = 32;
    private static final int MAX_COLLISION_RETRIES = 3;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MediaTicketProperties properties;
    private final SecureRandom random = new SecureRandom();

    public MediaTicketService(StringRedisTemplate redis, ObjectMapper objectMapper, MediaTicketProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * Issues a fresh ticket bound to the given document/user. Returns the
     * opaque token to embed in the URL.
     */
    public String issue(MediaTicket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        String payload = serialize(ticket);
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String token = generateToken();
            Boolean created = redis.opsForValue().setIfAbsent(KEY_PREFIX + token, payload, properties.ticketTtl());
            if (Boolean.TRUE.equals(created)) {
                log.debug(
                        "Media ticket issued docId={} passportId={} userId={}",
                        ticket.documentId(),
                        ticket.passportId(),
                        ticket.issuedBy());
                return token;
            }
        }
        // 32 bytes of entropy makes this effectively impossible. If we somehow
        // got here, something is very wrong with the RNG and we should fail loud.
        throw new IllegalStateException("Failed to allocate a unique media ticket after retries");
    }

    /**
     * Atomically reads and deletes a ticket. Returns empty if the ticket is
     * unknown or already redeemed/expired.
     */
    public Optional<MediaTicket> redeem(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redis.opsForValue().getAndDelete(KEY_PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(deserialize(value));
    }

    private String generateToken() {
        byte[] raw = new byte[TICKET_BYTES];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String serialize(MediaTicket ticket) {
        try {
            return objectMapper.writeValueAsString(ticket);
        } catch (JacksonException ex) {
            throw new IllegalStateException("Failed to serialize media ticket", ex);
        }
    }

    private MediaTicket deserialize(String value) {
        try {
            return objectMapper.readValue(value, MediaTicket.class);
        } catch (JacksonException ex) {
            // GETDEL has already removed the entry, so the ticket is gone either
            // way. Failing loud surfaces the underlying corruption (only writers
            // are us) instead of silently 404ing the user.
            throw new IllegalStateException("Failed to deserialize media ticket", ex);
        }
    }
}
