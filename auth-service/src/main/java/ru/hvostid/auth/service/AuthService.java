package ru.hvostid.auth.service;

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

import java.time.Instant;
import java.util.List;

/**
 * Core authentication service handling registration, login,
 * token introspection, refresh, and logout.
 */
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuthTokenProperties tokenProperties;

    public AuthService(UserRepository userRepository,
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
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.email(), request.name(), hashedPassword);
        user = userRepository.save(user);

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
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return createSession(user);
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
        return sessionRepository.findByAccessToken(request.token())
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .map(session -> {
                    User user = session.getUser();
                    List<String> roles = List.of(user.getRole().name().toLowerCase());
                    return IntrospectResponse.active(user.getId(), roles);
                })
                .orElse(IntrospectResponse.inactive());
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
        Session oldSession = sessionRepository.findByRefreshToken(request.refreshToken())
                .orElseThrow(InvalidRefreshTokenException::new);

        if (oldSession.getRefreshTokenExpiresAt().isBefore(Instant.now())) {
            sessionRepository.delete(oldSession);
            throw new InvalidRefreshTokenException();
        }

        User user = oldSession.getUser();
        sessionRepository.delete(oldSession);

        return createSession(user);
    }

    /**
     * Logout by deleting the session associated with the given access token.
     *
     * @param accessToken the Bearer token from Authorization header
     */
    @Transactional
    public void logout(String accessToken) {
        sessionRepository.findByAccessToken(accessToken)
                .ifPresent(sessionRepository::delete);
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

        Session session = new Session(user, accessToken, refreshToken,
                accessExpiresAt, refreshExpiresAt);
        sessionRepository.save(session);

        return new LoginResponse(accessToken, refreshToken, accessTtlSeconds);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().name().toLowerCase()
        );
    }
}
