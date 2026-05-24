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

    Page<PetPassport> findBySellerId(Long sellerId, Pageable pageable);
}
