package ru.hvostid.auth.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.auth.entity.AccountToken;
import ru.hvostid.auth.entity.User;
import ru.hvostid.auth.exception.UserNotFoundException;
import ru.hvostid.auth.repository.AccountTokenRepository;
import ru.hvostid.auth.repository.SessionRepository;
import ru.hvostid.auth.repository.UserRepository;

@Service
public class AccountLifecycleService {
    private final UserRepository users;
    private final AccountTokenRepository tokens;
    private final SessionRepository sessions;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final AccountMailOutbox mail;

    public AccountLifecycleService(
            UserRepository users,
            AccountTokenRepository tokens,
            SessionRepository sessions,
            TokenService tokenService,
            PasswordEncoder passwordEncoder,
            AccountMailOutbox mail) {
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.mail = mail;
    }

    @Transactional
    public void requestPasswordReset(String email) {
        mail.requireAvailable();
        users.findByEmailForUpdate(email).ifPresent(user -> issue(user, AccountToken.Purpose.PASSWORD_RESET));
    }

    @Transactional
    public void requestEmailVerification(Long userId) {
        mail.requireAvailable();
        User user = users.findLockedById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (!user.isEmailVerified()) issue(user, AccountToken.Purpose.EMAIL_VERIFICATION);
    }

    private void issue(User user, AccountToken.Purpose purpose) {
        Instant now = Instant.now();
        var existing = tokens.findByUserIdAndPurpose(user.getId(), purpose);
        if (existing.isPresent() && existing.get().getCreatedAt().isAfter(now.minusSeconds(60))) return;
        tokens.deleteByUserIdAndPurpose(user.getId(), purpose);
        tokens.flush();
        String token = tokenService.generateToken();
        AccountToken saved = tokens.save(
                new AccountToken(user, TokenService.hash(token), purpose, now.plus(Duration.ofMinutes(30))));
        mail.enqueue(saved, token);
    }

    @Transactional
    public void resetPassword(String token, String password) {
        AccountToken accountToken = consume(token, AccountToken.Purpose.PASSWORD_RESET);
        accountToken.getUser().setPasswordHash(passwordEncoder.encode(password));
        sessions.deleteByUserId(accountToken.getUser().getId());
    }

    @Transactional
    public void verifyEmail(String token) {
        AccountToken accountToken = consume(token, AccountToken.Purpose.EMAIL_VERIFICATION);
        accountToken.getUser().setEmailVerified(true);
    }

    private AccountToken consume(String token, AccountToken.Purpose purpose) {
        String hash = TokenService.hash(token);
        Long userId = tokens.findOwnerIdByTokenHash(hash).orElseThrow(this::invalidToken);
        users.findLockedById(userId).orElseThrow(this::invalidToken);
        AccountToken result = tokens.findByTokenHash(hash)
                .filter(value ->
                        value.getPurpose() == purpose && value.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(this::invalidToken);
        tokens.delete(result);
        return result;
    }

    private IllegalArgumentException invalidToken() {
        return new IllegalArgumentException("The account link is invalid or expired");
    }

    @Scheduled(fixedRateString = "${hvostid.auth.cleanup-interval:PT15M}")
    @Transactional
    public void cleanup() {
        tokens.deleteByExpiresAtLessThanEqual(Instant.now());
    }
}
