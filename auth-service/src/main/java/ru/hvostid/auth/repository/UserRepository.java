package ru.hvostid.auth.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.auth.entity.User;

/**
 * Spring Data repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
