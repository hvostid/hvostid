package ru.hvostid.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.auth.entity.Session;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data repository for {@link Session} entities.
 */
public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByAccessToken(String accessToken);

    Optional<Session> findByRefreshToken(String refreshToken);

    void deleteByUserId(Long userId);

    int deleteAllByExpiresAtBefore(Instant instant);
}
