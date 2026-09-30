package ru.hvostid.passport.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.passport.entity.PetPassport;

public interface PetPassportRepository extends JpaRepository<PetPassport, Long> {
    @EntityGraph(attributePaths = "vaccinations")
    Optional<PetPassport> findWithVaccinationsById(Long id);

    @EntityGraph(attributePaths = "vaccinations")
    List<PetPassport> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PetPassport p where p.id = :id")
    Optional<PetPassport> findLockedById(Long id);

    Page<PetPassport> findBySellerId(Long sellerId, Pageable pageable);
}
