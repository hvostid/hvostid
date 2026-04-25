package ru.hvostid.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.hvostid.auth.config.AuthTokenProperties;
import ru.hvostid.auth.dto.*;
import ru.hvostid.auth.entity.Session;
import ru.hvostid.auth.entity.User;
import ru.hvostid.auth.exception.EmailAlreadyExistsException;
import ru.hvostid.auth.exception.InvalidCredentialsException;
import ru.hvostid.auth.exception.InvalidRefreshTokenException;
import ru.hvostid.auth.repository.SessionRepository;
import ru.hvostid.auth.repository.UserRepository;
import ru.hvostid.common.contract.auth.IntrospectRequest;
import ru.hvostid.common.contract.auth.IntrospectResponse;
import ru.hvostid.common.security.UserRole;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    private static final Duration ACCESS_TTL = Duration.ofMinutes(30);
    private static final Duration REFRESH_TTL = Duration.ofDays(7);

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthTokenProperties props = new AuthTokenProperties(ACCESS_TTL, REFRESH_TTL);
        authService = new AuthService(
                userRepository, sessionRepository, passwordEncoder, tokenService, props
        );
    }

    // -- Registration tests --

    @Nested
    @DisplayName("register")
    class RegisterTests {
        @Test
        @DisplayName("success - returns user profile with buyer role")
        void register_success() {
            RegisterRequest request = new RegisterRequest("test@example.com", "password123", "Test User");

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(1L);
                return user;
            });

            UserResponse response = authService.register(request);

            assertEquals(1L, response.id());
            assertEquals("test@example.com", response.email());
            assertEquals("Test User", response.name());
            assertTrue(response.roles().contains("buyer"));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("duplicate email - throws EmailAlreadyExistsException")
        void register_duplicateEmail_throws() {
            RegisterRequest request = new RegisterRequest("dup@example.com", "password123", "Test");
            when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
            verify(userRepository, never()).save(any());
        }
    }

    // -- Login tests --

    @Nested
    @DisplayName("login")
    class LoginTests {
        @Test
        @DisplayName("success - returns tokens and saves session")
        void login_success() {
            LoginRequest request = new LoginRequest("test@example.com", "password123");

            User user = new User("test@example.com", "Test User", "hashed_password");
            user.setId(1L);

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
            when(tokenService.generateToken())
                    .thenReturn("access_token_value")
                    .thenReturn("refresh_token_value");

            LoginResponse response = authService.login(request);

            assertEquals("access_token_value", response.accessToken());
            assertEquals("refresh_token_value", response.refreshToken());
            assertEquals(ACCESS_TTL.toSeconds(), response.expiresIn());

            ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
            verify(sessionRepository).save(captor.capture());
            Session saved = captor.getValue();
            assertEquals("access_token_value", saved.getAccessToken());
            assertEquals("refresh_token_value", saved.getRefreshToken());
            assertNotNull(saved.getExpiresAt());
            assertNotNull(saved.getRefreshTokenExpiresAt());
        }

        @Test
        @DisplayName("user not found - throws InvalidCredentialsException")
        void login_userNotFound_throws() {
            LoginRequest request = new LoginRequest("missing@example.com", "password123");
            when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

            assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("wrong password - throws InvalidCredentialsException")
        void login_wrongPassword_throws() {
            LoginRequest request = new LoginRequest("test@example.com", "wrong_password");
            User user = new User("test@example.com", "Test User", "hashed_password");
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

            assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
            verify(sessionRepository, never()).save(any());
        }
    }

    // -- Introspect tests --

    @Nested
    @DisplayName("introspect")
    class IntrospectTests {
        @Test
        @DisplayName("valid token - returns active with userId and roles")
        void introspect_validToken_returnsActive() {
            User user = new User("test@example.com", "Test User", "hash");
            user.setId(42L);

            Session session = new Session(user, "valid_token", "refresh",
                    Instant.now().plusSeconds(600), Instant.now().plusSeconds(86400));

            when(sessionRepository.findByAccessToken("valid_token"))
                    .thenReturn(Optional.of(session));

            IntrospectResponse response = authService.introspect(new IntrospectRequest("valid_token"));

            assertTrue(response.active());
            assertEquals(42L, response.userId());
            assertEquals(List.of("buyer"), response.roles());
        }

        @Test
        @DisplayName("expired token - returns inactive")
        void introspect_expiredToken_returnsInactive() {
            User user = new User("test@example.com", "Test User", "hash");
            user.setId(1L);

            Session session = new Session(user, "expired_token", "refresh",
                    Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400));

            when(sessionRepository.findByAccessToken("expired_token"))
                    .thenReturn(Optional.of(session));

            IntrospectResponse response = authService.introspect(new IntrospectRequest("expired_token"));

            assertFalse(response.active());
            assertNull(response.userId());
            assertNull(response.roles());
        }

        @Test
        @DisplayName("non-existent token - returns inactive")
        void introspect_unknownToken_returnsInactive() {
            when(sessionRepository.findByAccessToken("unknown_token"))
                    .thenReturn(Optional.empty());

            IntrospectResponse response = authService.introspect(new IntrospectRequest("unknown_token"));

            assertFalse(response.active());
            assertNull(response.userId());
            assertNull(response.roles());
        }

        @Test
        @DisplayName("user with seller role - returns both roles in sorted order")
        void introspect_sellerRole_returnsBothRolesSorted() {
            User user = new User("seller@example.com", "Seller", "hash");
            user.setId(10L);
            user.addRole(UserRole.SELLER);

            Session session = new Session(user, "seller_token", "refresh",
                    Instant.now().plusSeconds(600), Instant.now().plusSeconds(86400));

            when(sessionRepository.findByAccessToken("seller_token"))
                    .thenReturn(Optional.of(session));

            IntrospectResponse response = authService.introspect(new IntrospectRequest("seller_token"));

            assertTrue(response.active());
            assertEquals(List.of("buyer", "seller"), response.roles());
        }
    }

    // -- Refresh tests --

    @Nested
    @DisplayName("refresh")
    class RefreshTests {
        @Test
        @DisplayName("valid refresh token - generates new pair and deletes old session")
        void refresh_validToken_generatesNewPair() {
            User user = new User("test@example.com", "Test User", "hash");
            user.setId(1L);

            Session oldSession = new Session(user, "old_access", "old_refresh",
                    Instant.now().plusSeconds(600),
                    Instant.now().plusSeconds(86400));
            oldSession.setId(100L);

            when(sessionRepository.findByRefreshToken("old_refresh"))
                    .thenReturn(Optional.of(oldSession));
            when(tokenService.generateToken())
                    .thenReturn("new_access")
                    .thenReturn("new_refresh");

            LoginResponse response = authService.refresh(new RefreshRequest("old_refresh"));

            assertEquals("new_access", response.accessToken());
            assertEquals("new_refresh", response.refreshToken());
            assertEquals(ACCESS_TTL.toSeconds(), response.expiresIn());

            verify(sessionRepository).delete(oldSession);
            verify(sessionRepository).save(any(Session.class));
        }

        @Test
        @DisplayName("non-existent refresh token - throws InvalidRefreshTokenException")
        void refresh_unknownToken_throws() {
            when(sessionRepository.findByRefreshToken("bad_token"))
                    .thenReturn(Optional.empty());

            RefreshRequest request = new RefreshRequest("bad_token");
            assertThrows(InvalidRefreshTokenException.class,
                    () -> authService.refresh(request));
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("expired refresh token - deletes session and throws")
        void refresh_expiredToken_throws() {
            User user = new User("test@example.com", "Test User", "hash");
            user.setId(1L);

            Session expiredSession = new Session(user, "access", "expired_refresh",
                    Instant.now().minusSeconds(3600),
                    Instant.now().minusSeconds(60));
            expiredSession.setId(200L);

            when(sessionRepository.findByRefreshToken("expired_refresh"))
                    .thenReturn(Optional.of(expiredSession));

            RefreshRequest request = new RefreshRequest("expired_refresh");
            assertThrows(InvalidRefreshTokenException.class,
                    () -> authService.refresh(request));

            verify(sessionRepository).delete(expiredSession);
            verify(sessionRepository, never()).save(any());
        }
    }

    // -- Logout tests --

    @Nested
    @DisplayName("logout")
    class LogoutTests {
        @Test
        @DisplayName("existing session - deletes session")
        void logout_existingSession_deletesIt() {
            User user = new User("test@example.com", "Test User", "hash");
            Session session = new Session(user, "token_to_revoke", "refresh",
                    Instant.now().plusSeconds(600), Instant.now().plusSeconds(86400));

            when(sessionRepository.findByAccessToken("token_to_revoke"))
                    .thenReturn(Optional.of(session));

            authService.logout("token_to_revoke");

            verify(sessionRepository).delete(session);
        }

        @Test
        @DisplayName("non-existent session - no-op, no exception")
        void logout_unknownToken_noOp() {
            when(sessionRepository.findByAccessToken("unknown"))
                    .thenReturn(Optional.empty());

            assertDoesNotThrow(() -> authService.logout("unknown"));
            verify(sessionRepository, never()).delete(any(Session.class));
        }
    }
}
