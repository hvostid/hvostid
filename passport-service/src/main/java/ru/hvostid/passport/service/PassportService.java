package ru.hvostid.passport.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.passport.dto.CreatePassportRequest;
import ru.hvostid.passport.dto.PassportResponse;
import ru.hvostid.passport.dto.UpdatePassportRequest;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.PassportAccessDeniedException;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PetPassportRepository;

@Service
public class PassportService {
    private static final Logger log = LoggerFactory.getLogger(PassportService.class);

    private final PetPassportRepository passportRepository;

    public PassportService(PetPassportRepository passportRepository) {
        this.passportRepository = passportRepository;
    }

    @Transactional
    public PassportResponse createPassport(CreatePassportRequest request, Long sellerId) {
        log.debug("Creating passport for sellerId={}", sellerId);
        PetPassport passport = new PetPassport(
                sellerId,
                normalize(request.species()),
                normalize(request.breed()),
                normalize(request.name()),
                request.birthDate(),
                request.gender(),
                normalize(request.color()),
                normalize(request.temperament()),
                normalize(request.specialNeeds()),
                request.neutered(),
                request.microchipped());

        PetPassport saved = passportRepository.save(passport);
        log.info("Passport created id={} sellerId={}", saved.getId(), saved.getSellerId());
        return PassportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PassportResponse getPassport(Long passportId) {
        log.debug("Getting passport id={}", passportId);
        PetPassport passport = getPassportWithVaccinations(passportId);
        return PassportResponse.from(passport);
    }

    @Transactional
    public PassportResponse updatePassport(Long passportId, UpdatePassportRequest request, Long sellerId) {
        log.debug("Updating passport id={} sellerId={}", passportId, sellerId);
        PetPassport passport = getPassportWithVaccinations(passportId);
        if (!passport.getSellerId().equals(sellerId)) {
            log.warn("Passport update denied id={} ownerId={} userId={}", passportId, passport.getSellerId(), sellerId);
            throw new PassportAccessDeniedException("You don't have permission to edit this passport");
        }

        if (request.species() != null) passport.setSpecies(normalize(request.species()));
        if (request.breed() != null) passport.setBreed(normalize(request.breed()));
        if (request.name() != null) passport.setName(normalize(request.name()));
        if (request.birthDate() != null) passport.setBirthDate(request.birthDate());
        if (request.gender() != null) passport.setGender(request.gender());
        if (request.color() != null) passport.setColor(normalize(request.color()));
        if (request.temperament() != null) passport.setTemperament(normalize(request.temperament()));
        if (request.specialNeeds() != null) passport.setSpecialNeeds(normalize(request.specialNeeds()));
        if (request.neutered() != null) passport.setNeutered(request.neutered());
        if (request.microchipped() != null) passport.setMicrochipped(request.microchipped());

        PetPassport updated = passportRepository.save(passport);
        log.info("Passport updated id={} sellerId={}", updated.getId(), sellerId);
        return PassportResponse.from(updated);
    }

    private PetPassport getPassportWithVaccinations(Long passportId) {
        return passportRepository
                .findWithVaccinationsById(passportId)
                .orElseThrow(() -> new PassportNotFoundException("Passport not found with id: " + passportId));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
