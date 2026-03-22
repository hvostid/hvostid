package ru.hvostid.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.hvostid.auth.config.AuthTokenProperties;
import ru.hvostid.auth.dto.LoginRequest;
import ru.hvostid.auth.dto.LoginResponse;
import ru.hvostid.auth.dto.RegisterRequest;
import ru.hvostid.auth.dto.UserResponse;
import ru.hvostid.auth.entity.Session;
import ru.hvostid.auth.entity.User;
import ru.hvostid.auth.exception.EmailAlreadyExistsException;
import ru.hvostid.auth.exception.InvalidCredentialsException;
import ru.hvostid.auth.repository.SessionRepository;
import ru.hvostid.auth.repository.UserRepository;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AuthTokenProperties props = new AuthTokenProperties(ACCESS_TTL, REFRESH_TTL);
        authService = new AuthService(
                userRepository, sessionRepository, passwordEncoder, tokenService, props
        );
    }

    // -- Registration tests --

    @Test
    @DisplayName("register - success - returns user profile with buyer role")
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
        assertEquals("buyer", response.role());

        // Verify password was hashed before saving
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed_password", captor.getValue().getPasswordHash());
    }

    @Test
    @DisplayName("register - duplicate email - throws EmailAlreadyExistsException")
    void register_duplicateEmail_throws409() {
        RegisterRequest request = new RegisterRequest("taken@example.com", "password123", "Test");

        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    // -- Login tests --

    @Test
    @DisplayName("login - success - returns tokens and expiresIn")
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        User user = new User("test@example.com", "Test User", "hashed_password");
        user.setId(1L);

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(tokenService.generateToken()).thenReturn("access_token_value", "refresh_token_value");
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = authService.login(request);

        assertEquals("access_token_value", response.accessToken());
        assertEquals("refresh_token_value", response.refreshToken());
        assertEquals(ACCESS_TTL.toSeconds(), response.expiresIn());

        // Verify session was saved
        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(captor.capture());
        Session savedSession = captor.getValue();
        assertEquals("access_token_value", savedSession.getAccessToken());
        assertEquals("refresh_token_value", savedSession.getRefreshToken());
        assertNotNull(savedSession.getExpiresAt());
    }

    @Test
    @DisplayName("login - user not found - throws InvalidCredentialsException")
    void login_userNotFound_throws401() {
        LoginRequest request = new LoginRequest("missing@example.com", "password123");

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("login - wrong password - throws InvalidCredentialsException")
    void login_wrongPassword_throws401() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong_password");

        User user = new User("test@example.com", "Test User", "hashed_password");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request));
        verify(sessionRepository, never()).save(any());
    }
}
