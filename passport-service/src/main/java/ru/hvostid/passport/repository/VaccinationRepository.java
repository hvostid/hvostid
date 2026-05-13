package ru.hvostid.passport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.passport.entity.Vaccination;

public interface VaccinationRepository extends JpaRepository<Vaccination, Long> {}
