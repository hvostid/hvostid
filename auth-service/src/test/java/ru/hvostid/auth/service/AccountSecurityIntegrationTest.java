package ru.hvostid.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static ru.hvostid.common.http.SecurityHeaders.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.hvostid.auth.dto.*;
import ru.hvostid.auth.exception.InvalidRefreshTokenException;
import ru.hvostid.common.contract.auth.IntrospectRequest;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        properties = {
            "hvostid.auth.mail.enabled=true",
            "hvostid.auth.mail.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "hvostid.auth.mail.initial-delay=PT1H"
        })
@AutoConfigureMockMvc
class AccountSecurityIntegrationTest extends AbstractPostgresContainerTest {
    @Autowired
    AuthService auth;

    @Autowired
    AccountMailOutbox outbox;

    @Autowired
    ru.hvostid.auth.repository.SessionRepository sessionRepository;

    @Autowired
    AccountLifecycleService accounts;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    SessionCleanupService cleanup;

    @Autowired
    ProfileService profiles;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    JavaMailSender mail;

    @AfterEach
    void clean() {
        jdbc.execute("TRUNCATE users, sessions, user_roles, account_tokens RESTART IDENTITY CASCADE");
    }

    private UserResponse register() {
        return auth.register(new RegisterRequest(" Owner@Example.com ", "password123", "Owner"));
    }

    private LoginResponse login() {
        return auth.login(new LoginRequest("OWNER@example.com", "password123"));
    }

    private String sentToken() {
        outbox.deliverPending();
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(captor.capture());
        return captor.getValue()
                .getText()
                .split("token=")[1]
                .lines()
                .findFirst()
                .orElseThrow();
    }

    @Test
    void canonicalEmailAndBytePasswordValidation() throws Exception {
        register();
        assertEquals("owner@example.com", profiles.getProfile(1L).email());
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                new RegisterRequest("OWNER@EXAMPLE.COM", "password123", "Other"))))
                .andExpect(status().isConflict());
        for (String password :
                List.of("a".repeat(73), String.valueOf((char) 0x0430).repeat(37))) {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    mapper.writeValueAsString(new RegisterRequest("new@example.com", password, "New"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("errors[0].field").value("password"));
        }
    }

    @Test
    void cleanupKeepsRefreshableSessionsAndTokensAreHashed() {
        register();
        LoginResponse tokens = login();
        assertEquals(
                TokenService.hash(tokens.accessToken()),
                jdbc.queryForObject("SELECT access_token FROM sessions", String.class));
        Instant expiry = sessionRepository
                .findByAccessToken(TokenService.hash(tokens.accessToken()))
                .orElseThrow()
                .getExpiresAt();
        assertTrue(expiry.isAfter(Instant.now().plus(29, ChronoUnit.MINUTES)));
        assertTrue(expiry.isBefore(Instant.now().plus(31, ChronoUnit.MINUTES)));
        jdbc.update("UPDATE sessions SET expires_at = TIMESTAMP '2000-01-01 00:00:00'");
        cleanup.cleanupExpiredSessions();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class));
        assertNotNull(auth.refresh(new RefreshRequest(tokens.refreshToken())));
        jdbc.update("UPDATE sessions SET refresh_token_expires_at = TIMESTAMP '2000-01-01 00:00:00'");
        cleanup.cleanupExpiredSessions();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    }

    @Test
    void concurrentRotationHasOneWinnerAndOneUnauthorizedLoser() throws Exception {
        register();
        LoginResponse tokens = login();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> call = () -> {
                start.await();
                try {
                    auth.refresh(new RefreshRequest(tokens.refreshToken()));
                    return true;
                } catch (InvalidRefreshTokenException ex) {
                    return false;
                }
            };
            var first = executor.submit(call);
            var second = executor.submit(call);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM sessions", Integer.class));
    }

    @Test
    void resetTokenIsSingleUseAndRevokesAllSessions() {
        register();
        LoginResponse first = login();
        login();
        accounts.requestPasswordReset("owner@example.com");
        String token = sentToken();
        assertEquals(
                TokenService.hash(token), jdbc.queryForObject("SELECT token_hash FROM account_tokens", String.class));
        accounts.resetPassword(token, "newpassword123");
        assertFalse(auth.introspect(new IntrospectRequest(first.accessToken())).active());
        assertTrue(auth.sessions(1L).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> accounts.resetPassword(token, "anotherpassword"));
        assertNotNull(auth.login(new LoginRequest("owner@example.com", "newpassword123")));
    }

    @Test
    void verificationIsOneUseAndExpiredLinkCannotVerify() {
        register();
        accounts.requestEmailVerification(1L);
        String token = sentToken();
        jdbc.update("UPDATE account_tokens SET expires_at = TIMESTAMP '2000-01-01 00:00:00'");
        assertThrows(IllegalArgumentException.class, () -> accounts.verifyEmail(token));
        jdbc.update("UPDATE account_tokens SET expires_at = TIMESTAMP '2099-01-01 00:00:00'");
        accounts.verifyEmail(token);
        assertTrue(profiles.getProfile(1L).emailVerified());
        assertThrows(IllegalArgumentException.class, () -> accounts.verifyEmail(token));
    }

    @Test
    void contactNeedsAuthenticationAndExplicitSellerConsent() throws Exception {
        register();
        profiles.addRole(1L, new AddRoleRequest("SELLER"));
        profiles.updateProfile(1L, new UpdateProfileRequest(null, "+1234567890", null, null));
        mvc.perform(get("/api/v1/users/1/contact")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/1/contact").header(USER_ID, "2").header(USER_ROLES, "BUYER"))
                .andExpect(status().isNotFound());
        profiles.updateProfile(1L, new UpdateProfileRequest(null, null, null, null, true));
        mvc.perform(get("/api/v1/users/1/contact").header(USER_ID, "2").header(USER_ROLES, "BUYER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("phone").value("+1234567890"))
                .andExpect(jsonPath("email").doesNotExist());
        profiles.updateProfile(1L, new UpdateProfileRequest(null, null, null, null, false));
        mvc.perform(get("/api/v1/users/1/contact").header(USER_ID, "2").header(USER_ROLES, "BUYER"))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeAllAndSessionDeletionAreOwnerScoped() {
        register();
        login();
        login();
        Long sessionId = auth.sessions(1L).getFirst().id();
        auth.register(new RegisterRequest("other@example.com", "password123", "Other"));
        auth.revokeSession(2L, sessionId);
        assertEquals(2, auth.sessions(1L).size());
        auth.revokeSession(1L, sessionId);
        assertEquals(1, auth.sessions(1L).size());
        auth.logoutAll(1L);
        assertTrue(auth.sessions(1L).isEmpty());
    }

    @Test
    void configuredSmtpFailureReturnsGenericAcceptedAndRetainsEncryptedRetry() throws Exception {
        register();
        doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
                .when(mail)
                .send(any(SimpleMailMessage.class));
        for (String email : List.of("owner@example.com", "unknown@example.com")) {
            mvc.perform(post("/api/v1/auth/password-reset/request")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(new PasswordResetRequest(email))))
                    .andExpect(status().isAccepted());
        }
        verify(mail, never()).send(any(SimpleMailMessage.class));
        assertEquals(1, outbox.pendingCount());
        String payload = jdbc.queryForObject("SELECT encrypted_payload FROM account_mail_outbox", String.class);
        assertFalse(payload.contains("owner@example.com"));
        assertFalse(payload.contains("reset-password"));
        outbox.deliverPending();
        assertEquals(1, outbox.pendingCount());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM account_mail_outbox", Integer.class));
        doNothing().when(mail).send(any(SimpleMailMessage.class));
        jdbc.update("UPDATE account_mail_outbox SET next_attempt_at = TIMESTAMPTZ '2000-01-01 00:00:00+00'");
        outbox.deliverPending();
        assertEquals(0, outbox.pendingCount());
        var delivered = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(2)).send(delivered.capture());
        String token = delivered
                .getValue()
                .getText()
                .split("token=")[1]
                .lines()
                .findFirst()
                .orElseThrow();
        accounts.resetPassword(token, "deliveredpassword123");
        assertNotNull(auth.login(new LoginRequest("owner@example.com", "deliveredpassword123")));
    }

    @Test
    void rollbackPublishesNeitherRecoveryTokenNorMail() {
        register();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    accounts.requestPasswordReset("owner@example.com");
                    status.setRollbackOnly();
                });
        assertEquals(0, outbox.pendingCount());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM account_tokens", Integer.class));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void expiredPendingMailIsDeletedWithoutDelivery() {
        register();
        accounts.requestPasswordReset("owner@example.com");
        jdbc.update("UPDATE account_mail_outbox SET expires_at = TIMESTAMPTZ '2000-01-01 00:00:00+00'");
        outbox.deliverPending();
        assertEquals(0, outbox.pendingCount());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }
}
