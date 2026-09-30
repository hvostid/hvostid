package ru.hvostid.auth.service;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hvostid.auth.config.AccountMailProperties;
import ru.hvostid.auth.entity.AccountToken;

@Service
public class AccountMailOutbox {
    private static final Logger log = LoggerFactory.getLogger(AccountMailOutbox.class);
    private final JdbcClient jdbc;
    private final AccountMailCipher cipher;
    private final AccountMailSender mail;
    private final AccountMailProperties properties;
    private final TransactionTemplate transaction;
    private final MeterRegistry metrics;

    public AccountMailOutbox(
            JdbcClient jdbc,
            AccountMailCipher cipher,
            AccountMailSender mail,
            AccountMailProperties properties,
            PlatformTransactionManager manager,
            MeterRegistry metrics) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.mail = mail;
        this.properties = properties;
        this.metrics = metrics;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        metrics.gauge("auth.mail.outbox.pending", this, box -> box.pendingCount());
    }

    public void requireAvailable() {
        mail.requireAvailable();
    }

    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(AccountToken token, String credential) {
        boolean reset = token.getPurpose() == AccountToken.Purpose.PASSWORD_RESET;
        String encrypted = cipher.encrypt(new AccountMailCipher.Message(
                token.getUser().getEmail(),
                reset ? "/reset-password" : "/verify-email",
                credential,
                reset ? "Reset your HvostID password" : "Verify your HvostID email"));
        jdbc.sql(
                        "INSERT INTO account_mail_outbox(account_token_id, encrypted_payload, expires_at, next_attempt_at) VALUES (:token,:payload,:expiry,:now)")
                .param("token", token.getId())
                .param("payload", encrypted)
                .param("expiry", token.getExpiresAt().atOffset(ZoneOffset.UTC))
                .param("now", Instant.now().atOffset(ZoneOffset.UTC))
                .update();
    }

    public long pendingCount() {
        return jdbc.sql("SELECT count(*) FROM account_mail_outbox")
                .query(Long.class)
                .single();
    }

    @Scheduled(
            fixedDelayString = "${hvostid.auth.mail.poll-interval:PT15S}",
            initialDelayString = "${hvostid.auth.mail.initial-delay:PT15S}")
    public void deliverPending() {
        Instant now = Instant.now();
        int expired = jdbc.sql("DELETE FROM account_mail_outbox WHERE expires_at<=:now")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update();
        if (expired > 0) {
            metrics.counter("auth.mail.delivery", "result", "expired").increment(expired);
            log.warn("Expired undelivered account email messages count={}", expired);
        }
        if (!properties.enabled()) return;
        List<Long> ids = jdbc.sql(
                        "SELECT id FROM account_mail_outbox WHERE next_attempt_at<=:now AND expires_at>:now ORDER BY id LIMIT 10")
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .list();
        for (Long id : ids) transaction.executeWithoutResult(status -> deliver(id));
    }

    private void deliver(Long id) {
        var pending = jdbc.sql(
                        "SELECT encrypted_payload,attempts FROM account_mail_outbox WHERE id=:id AND next_attempt_at<=:now AND expires_at>:now FOR UPDATE SKIP LOCKED")
                .param("id", id)
                .param("now", Instant.now().atOffset(ZoneOffset.UTC))
                .query((row, number) -> new Pending(row.getString("encrypted_payload"), row.getInt("attempts")))
                .optional();
        if (pending.isEmpty()) return;
        try {
            var message = cipher.decrypt(pending.get().payload());
            mail.send(message.email(), message.path(), message.token(), message.subject());
        } catch (RuntimeException ex) {
            int attempts = pending.get().attempts() + 1;
            long retrySeconds = Math.min(300, 30L << Math.min(attempts - 1, 4));
            jdbc.sql("UPDATE account_mail_outbox SET attempts=:attempts,next_attempt_at=:retry WHERE id=:id")
                    .param("attempts", attempts)
                    .param("retry", Instant.now().plusSeconds(retrySeconds).atOffset(ZoneOffset.UTC))
                    .param("id", id)
                    .update();
            // SMTP errors may include recipients or message content. Record only non-sensitive identifiers.
            log.warn("Account email delivery deferred messageId={} attempt={}", id, attempts);
            metrics.counter("auth.mail.delivery", "result", "failure").increment();
            return;
        }
        jdbc.sql("DELETE FROM account_mail_outbox WHERE id=:id").param("id", id).update();
        metrics.counter("auth.mail.delivery", "result", "success").increment();
    }

    private record Pending(String payload, int attempts) {}
}
