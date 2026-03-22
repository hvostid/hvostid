package ru.hvostid.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Instant;

/**
 * Core authentication service handling registration and login.
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

        String accessToken = tokenService.generateToken();
        String refreshToken = tokenService.generateToken();

        long ttlSeconds = tokenProperties.accessTokenTtl().toSeconds();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        Session session = new Session(user, accessToken, refreshToken, expiresAt);
        sessionRepository.save(session);

        return new LoginResponse(accessToken, refreshToken, ttlSeconds);
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
