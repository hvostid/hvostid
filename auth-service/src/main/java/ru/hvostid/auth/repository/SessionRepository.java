package ru.hvostid.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.hvostid.auth.entity.Session;

/**
 * Spring Data repository for {@link Session} entities.
 */
public interface SessionRepository extends JpaRepository<Session, Long> {
    Optional<Session> findByAccessToken(String accessToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Session> findByRefreshToken(String refreshToken);

    @Query("select s.user.id from Session s where s.refreshToken = :tokenHash")
    Optional<Long> findOwnerIdByRefreshToken(String tokenHash);

    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserId(Long userId);

    int deleteAllByRefreshTokenExpiresAtLessThanEqual(Instant instant);
}
