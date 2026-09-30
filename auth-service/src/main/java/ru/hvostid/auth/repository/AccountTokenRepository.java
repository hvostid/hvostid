package ru.hvostid.auth.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.hvostid.auth.entity.AccountToken;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Query("select t.user.id from AccountToken t where t.tokenHash = :tokenHash")
    Optional<Long> findOwnerIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHash(String tokenHash);

    Optional<AccountToken> findByUserIdAndPurpose(Long userId, AccountToken.Purpose purpose);

    void deleteByUserIdAndPurpose(Long userId, AccountToken.Purpose purpose);

    void deleteByExpiresAtLessThanEqual(Instant instant);
}
