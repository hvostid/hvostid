package ru.hvostid.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.auth.entity.User;

import java.util.Optional;

/**
 * Spring Data repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
