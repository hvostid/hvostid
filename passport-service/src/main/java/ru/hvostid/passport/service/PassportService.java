package ru.hvostid.passport.service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.dto.CreatePassportRequest;
import ru.hvostid.passport.dto.PassportResponse;
import ru.hvostid.passport.dto.UpdatePassportRequest;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.PassportInUseException;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PetPassportRepository;

@Service
public class PassportService {
    private static final Logger log = LoggerFactory.getLogger(PassportService.class);

    private final PetPassportRepository passportRepository;
    private final PassportAccessService accessService;
    private final TrustScoreService trustScoreService;
    private final ListingServiceClient listingServiceClient;
    private final PassportDocumentService documentService;
    private final PassportReferenceService references;
    private final ru.hvostid.passport.repository.PassportDocumentRepository documents;

    public PassportService(
            PetPassportRepository passportRepository,
            PassportAccessService accessService,
            TrustScoreService trustScoreService,
            ListingServiceClient listingServiceClient,
            PassportDocumentService documentService,
            PassportReferenceService references,
            ru.hvostid.passport.repository.PassportDocumentRepository documents) {
        this.passportRepository = passportRepository;
        this.accessService = accessService;
        this.trustScoreService = trustScoreService;
        this.listingServiceClient = listingServiceClient;
        this.documentService = documentService;
        this.references = references;
        this.documents = documents;
    }

    @Transactional
    public PassportResponse createPassport(CreatePassportRequest request, Long sellerId) {
        log.debug("Creating passport for sellerId={}", sellerId);
        PetPassport passport = PetPassport.builder()
                .sellerId(sellerId)
                .species(normalize(request.species()))
                .breed(normalize(request.breed()))
                .name(normalize(request.name()))
                .birthDate(request.birthDate())
                .gender(request.gender())
                .color(normalize(request.color()))
                .temperament(normalize(request.temperament()))
                .specialNeeds(normalize(request.specialNeeds()))
                .neutered(request.neutered())
                .microchipped(request.microchipped())
                .build();

        replaceVaccinations(passport, request.vaccinations());
        PetPassport saved = passportRepository.save(passport);
        log.info("Passport created id={} sellerId={}", saved.getId(), saved.getSellerId());
        trustScoreService.recalculate(saved.getId());
        return PassportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PassportResponse> listOwnedBy(Long sellerId) {
        log.debug("Listing passports for sellerId={}", sellerId);
        return passportRepository.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(PassportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PassportResponse> getMyPassports(Long sellerId, Pageable pageable) {
        log.debug("Listing paged passports for sellerId={} pageable={}", sellerId, pageable);
        var page = passportRepository.findBySellerId(sellerId, pageable);
        if (page.isEmpty()) return page.map(PassportResponse::from);
        var covers = documents
                .findCovers(page.getContent().stream().map(PetPassport::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        ru.hvostid.passport.repository.PassportDocumentRepository.CoverRef::getPassportId,
                        ru.hvostid.passport.repository.PassportDocumentRepository.CoverRef::getDocumentId));
        return page.map(passport -> PassportResponse.from(passport, covers.get(passport.getId())));
    }

    @Transactional(readOnly = true)
    public PassportResponse getPassport(Long passportId, Long userId, Set<String> userRoles) {
        log.debug("Getting passport id={} userId={} roles={}", passportId, userId, userRoles);
        PetPassport passport = getPassportWithVaccinations(passportId);
        accessService.requireCanView(passport, userId, userRoles);
        return PassportResponse.from(passport);
    }

    @Transactional(readOnly = true)
    public PassportResponse getPassportForInternal(Long passportId) {
        log.debug("Internal passport read id={}", passportId);
        PetPassport passport = getPassportWithVaccinations(passportId);
        return PassportResponse.from(passport);
    }

    @Transactional
    public PassportResponse updatePassport(Long passportId, UpdatePassportRequest request, Long sellerId) {
        log.debug("Updating passport id={} sellerId={}", passportId, sellerId);
        PetPassport passport = references.lock(passportId);
        accessService.requireOwner(passport, sellerId, "edit");
        requireEditable(passportId, null);

        if (request.species() != null && request.species().isBlank()
                || request.name() != null && request.name().isBlank())
            throw new ru.hvostid.common.exception.ValidationException("Name and species cannot be blank");
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

        replaceVaccinations(passport, request.vaccinations());
        passport.setModerated(false);
        PetPassport updated = passportRepository.save(passport);
        log.info("Passport updated id={} sellerId={}", updated.getId(), sellerId);
        trustScoreService.recalculate(updated.getId());
        return PassportResponse.from(updated);
    }

    @Transactional
    public void deletePassport(Long passportId, Long userId, String requestId) {
        PetPassport passport = references.lock(passportId);
        accessService.requireOwner(passport, userId, "delete");
        requireEditable(passportId, requestId);
        documentService.enqueueCleanupForPassport(passportId);
        passportRepository.delete(passport);
        log.info("Passport deleted id={} sellerId={}; object cleanup queued", passportId, userId);
    }

    private void requireEditable(Long id, String requestId) {
        references.requireUnreferenced(id);
        // Legacy active listings predate reservations. Their read check runs while the passport row is locked;
        // every new publication must acquire that same row before committing.
        if (listingServiceClient.hasActiveListingForPassport(id, requestId))
            throw new PassportInUseException("Archive the active listing before editing or deleting this passport");
    }

    private void replaceVaccinations(
            PetPassport passport, java.util.List<ru.hvostid.passport.dto.VaccinationRequest> vaccinations) {
        if (vaccinations == null) {
            if (passport.getVaccinations().stream().anyMatch(v -> v.getDate().isBefore(passport.getBirthDate())))
                throw new ru.hvostid.common.exception.ValidationException(
                        "Birth date cannot follow an existing vaccination date");
            return;
        }
        passport.getVaccinations().clear();
        for (var v : vaccinations) {
            if (v.date().isBefore(passport.getBirthDate()))
                throw new ru.hvostid.common.exception.ValidationException(
                        "Vaccination date cannot precede the animal's birth date");
            passport.getVaccinations()
                    .add(new ru.hvostid.passport.entity.Vaccination(
                            passport, v.name().trim(), v.date(), v.nextDate(), false));
        }
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
