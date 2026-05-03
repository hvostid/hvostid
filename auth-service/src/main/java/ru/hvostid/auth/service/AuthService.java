package ru.hvostid.auth.service;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * Core authentication service handling registration, login,
 * token introspection, refresh, and logout.
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthTokenProperties tokenProperties;

    public AuthService(
            UserRepository userRepository,
            SessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AuthTokenProperties tokenProperties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.tokenProperties = tokenProperties;
    }

    /**
     * Register a new user.
     *
     * @param request registration data
     * @return created user profile (without sensitive data)
     * @throws EmailAlreadyExistsException if the email is already taken
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("Registering user with email={}", request.email());

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration failed: email already exists email={}", request.email());
            throw new EmailAlreadyExistsException(request.email());
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), request.name(), hashedPassword);
        user = userRepository.save(user);

        log.info("User registered userId={} email={} roles={}", user.getId(), user.getEmail(), user.getRoles());
        return toUserResponse(user);
    }

    /**
     * Authenticate a user and create a new session.
     *
     * @param request login credentials
     * @return access and refresh tokens with expiry info
     * @throws InvalidCredentialsException if credentials are invalid
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.debug("Login attempt email={}", request.email());

        User user = userRepository.findByEmail(request.email()).orElseThrow(() -> {
            log.warn("Login failed: user not found email={}", request.email());
            return new InvalidCredentialsException();
        });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: wrong password userId={} email={}", user.getId(), user.getEmail());
            throw new InvalidCredentialsException();
        }

        LoginResponse response = createSession(user);
        log.info("Login successful userId={} email={}", user.getId(), user.getEmail());
        return response;
    }

    /**
     * Validate an access token and return user info if active.
     * Called by Gateway on every protected request.
     *
     * @param request contains the access token to validate
     * @return active status with userId and roles, or inactive
     */
    @Transactional(readOnly = true)
    public IntrospectResponse introspect(IntrospectRequest request) {
        log.debug("Introspect requested");

        return sessionRepository
                .findByAccessToken(request.token())
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .map(session -> {
                    User user = session.getUser();
                    List<String> roles = user.getRoles().stream()
                            .map(UserRole::value)
                            .sorted()
                            .toList();
                    log.debug("Introspect result: active=true userId={} roles={}", user.getId(), roles);
                    return IntrospectResponse.active(user.getId(), roles);
                })
                .orElseGet(() -> {
                    log.debug("Introspect result: active=false");
                    return IntrospectResponse.inactive();
                });
    }

    /**
     * Refresh a session by generating new token pair and deleting the old session.
     *
     * @param request contains the refresh token
     * @return new access and refresh tokens with expiry info
     * @throws InvalidRefreshTokenException if refresh token is invalid or expired
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        log.debug("Refresh token requested");

        Session oldSession = sessionRepository
                .findByRefreshToken(request.refreshToken())
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token not found");
                    return new InvalidRefreshTokenException();
                });

        if (oldSession.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            log.warn(
                    "Refresh failed: token expired userId={} sessionId={}",
                    oldSession.getUser().getId(),
                    oldSession.getId());
            sessionRepository.delete(oldSession);
            throw new InvalidRefreshTokenException();
        }

        User user = oldSession.getUser();
        sessionRepository.delete(oldSession);

        LoginResponse response = createSession(user);
        log.info("Token refreshed userId={} oldSessionId={}", user.getId(), oldSession.getId());
        return response;
    }

    /**
     * Logout by deleting the session associated with the given access token.
     *
     * @param accessToken the Bearer token from Authorization header
     */
    @Transactional
    public void logout(String accessToken) {
        log.debug("Logout requested");

        sessionRepository
                .findByAccessToken(accessToken)
                .ifPresentOrElse(
                        session -> {
                            Long userId = session.getUser().getId();
                            sessionRepository.delete(session);
                            log.info("Logout successful userId={} sessionId={}", userId, session.getId());
                        },
                        () -> log.debug("Logout: session not found, no-op"));
    }

    /**
     * Create a new session for the user and return the token response.
     */
    private LoginResponse createSession(User user) {
        String accessToken = tokenService.generateToken();
        String refreshToken = tokenService.generateToken();

        Instant now = Instant.now();
        long accessTtlSeconds = tokenProperties.accessTokenTtl().toSeconds();
        Instant accessExpiresAt = now.plusSeconds(accessTtlSeconds);
        Instant refreshExpiresAt = now.plus(tokenProperties.refreshTokenTtl());

        Session session = new Session(user, accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
        sessionRepository.save(session);

        log.debug(
                "Session created userId={} accessTtl={}s refreshTtl={}",
                user.getId(),
                accessTtlSeconds,
                tokenProperties.refreshTokenTtl());
        return new LoginResponse(accessToken, refreshToken, accessTtlSeconds);
    }

    private UserResponse toUserResponse(User user) {
        List<String> roles =
                user.getRoles().stream().map(UserRole::value).sorted().toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), roles);
    }
}
