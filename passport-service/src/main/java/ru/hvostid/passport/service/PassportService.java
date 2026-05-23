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
import ru.hvostid.passport.service.PassportDocumentService.DocumentCleanupResult;

@Service
public class PassportService {
    private static final Logger log = LoggerFactory.getLogger(PassportService.class);

    private final PetPassportRepository passportRepository;
    private final PassportAccessService accessService;
    private final TrustScoreService trustScoreService;
    private final ListingServiceClient listingServiceClient;
    private final PassportDocumentService documentService;

    public PassportService(
            PetPassportRepository passportRepository,
            PassportAccessService accessService,
            TrustScoreService trustScoreService,
            ListingServiceClient listingServiceClient,
            PassportDocumentService documentService) {
        this.passportRepository = passportRepository;
        this.accessService = accessService;
        this.trustScoreService = trustScoreService;
        this.listingServiceClient = listingServiceClient;
        this.documentService = documentService;
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
        return passportRepository.findBySellerId(sellerId, pageable).map(PassportResponse::from);
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
        PetPassport passport = getPassportWithVaccinations(passportId);
        accessService.requireOwner(passport, sellerId, "edit");

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
        trustScoreService.recalculate(updated.getId());
        return PassportResponse.from(updated);
    }

    @Transactional
    public void deletePassport(Long passportId, Long userId, String requestId) {
        PetPassport passport = accessService.getExistingPassport(passportId);
        accessService.requireOwner(passport, userId, "delete");

        if (listingServiceClient.hasPublishedListingForPassport(passportId, requestId)) {
            throw new PassportInUseException(
                    "Passport is referenced by a published listing; archive or delete the listing first.");
        }

        DocumentCleanupResult cleanup = documentService.deleteAllForPassport(passportId);
        passportRepository.delete(passport);
        log.info(
                "Passport deleted id={} sellerId={} documentsCleaned={} documentsFailed={}",
                passportId,
                userId,
                cleanup.cleaned(),
                cleanup.failed());
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
