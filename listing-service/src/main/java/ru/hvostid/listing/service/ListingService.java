package ru.hvostid.listing.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.listing.ListingConstants;
import ru.hvostid.listing.dto.ListingFilterRequest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.entity.ListingStatusHistory;
import ru.hvostid.listing.exception.*;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingSpecifications;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);
    private static final String LISTING_NOT_FOUND_MESSAGE = "Listing not found with id: ";

    private final ListingFlagRepository flagRepository;
    private final ListingRepository listingRepository;
    private final ListingStatusHistoryRepository historyRepository;
    private final ListingDraftService draftService;
    private final ru.hvostid.listing.client.PassportServiceClient passports;
    private final PassportReferenceJobService referenceJobs;

    public ListingService(
            ListingFlagRepository flagRepository,
            ListingRepository listingRepository,
            ListingStatusHistoryRepository historyRepository,
            ListingDraftService draftService,
            ru.hvostid.listing.client.PassportServiceClient passports,
            PassportReferenceJobService referenceJobs) {
        this.flagRepository = flagRepository;
        this.listingRepository = listingRepository;
        this.historyRepository = historyRepository;
        this.draftService = draftService;
        this.passports = passports;
        this.referenceJobs = referenceJobs;
    }

    /**
     * Shared "load or 404" helper used by this service and by sibling services
     * (e.g. {@link ModerationService}) so the not-found message stays consistent.
     */
    Listing requireListing(Long id) {
        return listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException(LISTING_NOT_FOUND_MESSAGE + id));
    }

    @Transactional
    public ListingResponse createListing(ListingRequest request, Long sellerId) {
        log.debug("Creating listing for sellerId={}", sellerId);

        if (normalize(request.title()).length() < 3)
            throw new ru.hvostid.common.exception.ValidationException(
                    "Title must contain at least 3 non-whitespace characters");
        checkForDuplicate(request, sellerId);
        long passportId = parsePassportId(request.passportId());
        passports.validateOwner(passportId, sellerId);

        Listing listing = Listing.builder()
                .sellerId(sellerId)
                .title(normalize(request.title()))
                .description(normalize(request.description()))
                .species(normalize(request.species()))
                .breed(normalize(request.breed()))
                .age(request.age())
                .price(request.price())
                .city(normalize(request.city()))
                .passportId(Long.toString(passportId))
                .build();

        Listing saved = listingRepository.save(listing);
        draftService.deleteDraft(sellerId);
        log.info("Listing created id={} sellerId={}", saved.getId(), saved.getSellerId());

        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ListingResponse getListing(Long id, Long userId, Set<String> userRoles) {
        log.debug("Getting listing id={} for userId={} roles={}", id, userId, userRoles);

        Listing listing = requireListing(id);

        boolean isOwner = listing.getSellerId().equals(userId);
        boolean isPublished = listing.getStatus() == ListingStatus.PUBLISHED;
        boolean isPrivileged = userRoles != null
                && (userRoles.contains(UserRole.MODERATOR.value()) || userRoles.contains(UserRole.ADMIN.value()));

        if (!isPublished && !isOwner && !isPrivileged) {
            log.warn("Access denied to listing id={} for userId={}, status={}", id, userId, listing.getStatus());
            throw new AccessDeniedException("You don't have permission to view this listing");
        }

        return ListingResponse.from(listing);
    }

    @Transactional
    public ListingResponse updateListing(Long id, ListingUpdateRequest request, Long userId) {
        log.debug("Updating listing id={} for userId={}", id, userId);

        Listing listing = requireLockedListing(id);

        if (!listing.getSellerId().equals(userId)) {
            log.warn("Update denied: not owner listingId={} userId={}", id, userId);
            throw new AccessDeniedException("You don't have permission to edit this listing");
        }

        if (listing.getStatus() != ListingStatus.DRAFT
                && listing.getStatus() != ListingStatus.PUBLISHED
                && listing.getStatus() != ListingStatus.REJECTED) {
            throw new InvalidListingStatusException("Cannot edit listing in status: " + listing.getStatus()
                    + ". Only DRAFT/PUBLISHED/REJECTED listings can be edited.");
        }

        if (request.title() != null && normalize(request.title()).length() < 3)
            throw new ru.hvostid.common.exception.ValidationException(
                    "Title must contain at least 3 non-whitespace characters");
        if (request.species() != null && request.species().isBlank()
                || request.city() != null && request.city().isBlank())
            throw new ru.hvostid.common.exception.ValidationException("Species and city cannot be blank");
        if (request.passportId() != null
                && !canonicalPassportId(request.passportId()).equals(canonicalPassportId(listing.getPassportId())))
            throw new ru.hvostid.common.exception.ValidationException(
                    "Passport cannot be changed; create a new listing instead");
        if (request.title() != null) listing.setTitle(normalize(request.title()));
        if (request.description() != null) listing.setDescription(normalize(request.description()));
        if (request.species() != null) listing.setSpecies(normalize(request.species()));
        if (request.breed() != null) listing.setBreed(normalize(request.breed()));
        if (request.age() != null) listing.setAge(request.age());
        if (request.price() != null) listing.setPrice(request.price());
        if (request.city() != null) listing.setCity(normalize(request.city()));

        if (listing.getStatus() == ListingStatus.PUBLISHED) {
            long passportId = parsePassportId(listing.getPassportId());
            long operation = referenceJobs.prepare(listing.getId(), passportId, listing.getSellerId());
            passports.acquire(passportId, listing.getId(), listing.getSellerId(), false, operation * 2);
            listing.setStatus(ListingStatus.MODERATION);
            historyRepository.save(new ListingStatusHistory(
                    id,
                    ListingStatus.PUBLISHED,
                    ListingStatus.MODERATION,
                    userId,
                    "OWNER",
                    "Content edited; review required"));
        }
        Listing updated = listingRepository.save(listing);
        log.info("Listing updated id={} userId={}", updated.getId(), userId);

        return ListingResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getPublishedListings(Pageable pageable) {
        log.debug("Getting published listings, page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return findPublishedListings(pageable);
    }

    private Page<ListingResponse> findPublishedListings(Pageable pageable) {
        return listingRepository.findByStatus(ListingStatus.PUBLISHED, pageable).map(ListingResponse::from);
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedListingForPassport(String passportId) {
        return hasPublishedListingForPassport(passportId, null);
    }

    @Transactional(readOnly = true)
    public boolean hasPublishedListingForPassport(String passportId, Long sellerId) {
        if (passportId == null || passportId.isBlank()) {
            return false;
        }
        String id = canonicalPassportId(passportId);
        if (sellerId != null)
            return listingRepository.existsByPassportIdInAndStatusAndSellerId(
                    List.of(id, "passport-" + id), ListingStatus.PUBLISHED, sellerId);
        return listingRepository.existsByPassportIdInAndStatusIn(
                List.of(id, "passport-" + id), Set.of(ListingStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveListingForPassport(Long passportId) {
        return listingRepository.existsByPassportIdInAndStatusIn(
                List.of(passportId.toString(), "passport-" + passportId),
                Set.of(ListingStatus.MODERATION, ListingStatus.PUBLISHED));
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getMyListings(Long sellerId, ListingStatus status, Pageable pageable) {
        log.debug(
                "Getting own listings sellerId={} status={} page={} size={}",
                sellerId,
                status,
                pageable.getPageNumber(),
                pageable.getPageSize());
        Page<Listing> page = status == null
                ? listingRepository.findBySellerId(sellerId, pageable)
                : listingRepository.findBySellerIdAndStatus(sellerId, status, pageable);
        return page.map(ListingResponse::from);
    }

    @Transactional
    public ListingResponse updateStatus(Long id, StatusUpdateRequest request, Long userId, Set<String> userRoles) {
        log.debug("Updating status listingId={} to {} by userId={}, roles={}", id, request.status(), userId, userRoles);

        Listing listing = requireLockedListing(id);

        boolean isOwner = listing.getSellerId().equals(userId);
        ListingStatus oldStatus = listing.getStatus();
        ListingStatus newStatus = request.status();

        StatusTransition transition = StatusTransitionValidator.validateTransition(oldStatus, newStatus);

        StatusTransitionValidator.checkPermissions(transition, isOwner, userRoles);

        if (StatusTransitionValidator.isCommentRequired(transition)
                && (request.comment() == null || request.comment().isBlank())) {
            throw new InvalidStatusTransitionException(
                    String.format("Comment is required for transition from %s to %s", oldStatus, newStatus));
        }

        String normalizedComment =
                (request.comment() != null && !request.comment().isBlank()) ? request.comment() : null;
        listing.setModerationComment(normalizedComment);

        // Unarchiving makes the title visible again under the active-title
        // unique rule, so re-check for duplicates against the seller's other
        // non-ARCHIVED listings. The check excludes the current row because
        // it is still ARCHIVED at this point.
        if (oldStatus == ListingStatus.ARCHIVED && newStatus != ListingStatus.ARCHIVED) {
            checkForDuplicateTitle(listing.getSellerId(), listing.getTitle());
        }

        Long passportId = existingPassportId(listing);
        if (newStatus == ListingStatus.MODERATION || newStatus == ListingStatus.PUBLISHED) {
            // Legacy invalid references may be retired, but can never enter an active state.
            passportId = parsePassportId(listing.getPassportId());
            long operation = referenceJobs.prepare(listing.getId(), passportId, listing.getSellerId());
            passports.acquire(
                    passportId,
                    listing.getId(),
                    listing.getSellerId(),
                    newStatus == ListingStatus.PUBLISHED,
                    operation * 2);
        }
        if (passportId != null) referenceJobs.enqueue(listing.getId(), passportId, listing.getSellerId());
        listing.setStatus(newStatus);
        if (newStatus == ListingStatus.SOLD) {
            listing.setSoldAt(Instant.now());
        }
        Listing saved = listingRepository.save(listing);

        String role = determineRole(userRoles, isOwner);
        ListingStatusHistory history =
                new ListingStatusHistory(id, oldStatus, newStatus, userId, role, request.comment());
        historyRepository.save(history);

        log.info(
                "Status changed: listingId={}, from={}, to={}, userId={}, role={}, comment={}",
                id,
                oldStatus,
                newStatus,
                userId,
                role,
                request.comment());

        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> searchListings(String keyword, Pageable pageable) {
        log.debug(
                "Searching listings with keyword='{}', page={}, size={}",
                keyword,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (keyword == null || keyword.isBlank() || "\"\"".equals(keyword.trim())) {
            return findPublishedListings(pageable);
        }

        String sanitizedKeyword = normalizeKeyword(keyword);
        ListingFilterRequest emptyFilter = new ListingFilterRequest(null, null, null, null, null, null, null);

        return listingRepository
                .searchByKeyword(ListingStatus.PUBLISHED.name(), sanitizedKeyword, emptyFilter, pageable)
                .map(ListingResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getListingsWithFilters(ListingFilterRequest filters, Pageable pageable) {
        log.debug("Getting listings with filters: {}", filters);
        Specification<Listing> spec = ListingSpecifications.withFilters(filters);
        return listingRepository.findAll(spec, pageable).map(ListingResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> searchWithFilters(String keyword, ListingFilterRequest filters, Pageable pageable) {
        log.debug("Searching with keyword='{}' and filters: {}", keyword, filters);

        if (keyword == null || keyword.isBlank() || "\"\"".equals(keyword.trim())) {
            // Same path as getListingsWithFilters; inlined so the self-call doesn't
            // skip the Spring proxy (and so the @Transactional context stays explicit).
            Specification<Listing> spec = ListingSpecifications.withFilters(filters);
            return listingRepository.findAll(spec, pageable).map(ListingResponse::from);
        }

        String sanitizedKeyword = normalizeKeyword(keyword);

        // Deep-pagination cap: refuse to return matches past the configured horizon.
        // Bounded by ListingController (MAX_PAGE_SIZE), so we only need to guard the offset.
        if (pageable.getOffset() >= ListingConstants.MAX_SEARCH_RESULTS) {
            return Page.empty(pageable);
        }

        ListingFilterRequest effective = normalizeFilter(filters);

        Page<Listing> searchResults = listingRepository.searchByKeyword(
                ListingStatus.PUBLISHED.name(), sanitizedKeyword, effective, pageable);

        return searchResults.map(ListingResponse::from);
    }

    private static ListingFilterRequest normalizeFilter(ListingFilterRequest filters) {
        if (filters == null) {
            return new ListingFilterRequest(null, null, null, null, null, null, null);
        }
        return new ListingFilterRequest(
                blankToNull(filters.species()),
                blankToNull(filters.breed()),
                filters.ageMin(),
                filters.ageMax(),
                filters.priceMin(),
                filters.priceMax(),
                blankToNull(filters.city()));
    }

    @Transactional
    public void deleteListing(Long id, Long userId, Set<String> userRoles) {
        log.debug("Deleting listing id={} by userId={}, roles={}", id, userId, userRoles);

        Listing listing = requireLockedListing(id);

        boolean isOwner = listing.getSellerId().equals(userId);
        boolean isAdmin = userRoles != null && userRoles.contains(UserRole.ADMIN.value());

        if (!isOwner && !isAdmin) {
            log.warn("Delete denied: not owner and not admin listingId={} userId={}", id, userId);
            throw new AccessDeniedException("You don't have permission to delete this listing");
        }

        if (listing.getStatus() == ListingStatus.MODERATION) {
            log.warn("Delete denied: listing in MODERATION listingId={}", id);
            throw new ListingDeletionConflictException("Cannot delete a listing that is currently under moderation; "
                    + "wait for the review to finish or ask a moderator to reject it.");
        }

        ListingStatus oldStatus = listing.getStatus();
        Long listingId = listing.getId();

        Long passportId = existingPassportId(listing);
        if (passportId != null) referenceJobs.enqueue(listing.getId(), passportId, listing.getSellerId());
        // Delete related entities
        flagRepository.deleteByListingId(listingId);
        historyRepository.deleteByListingId(listingId);
        listingRepository.delete(listing);

        log.info("Listing deleted id={} userId={} oldStatus={}", id, userId, oldStatus);
    }

    private Listing requireLockedListing(Long id) {
        return listingRepository
                .findLockedById(id)
                .orElseThrow(() -> new ListingNotFoundException(LISTING_NOT_FOUND_MESSAGE + id));
    }

    public static String canonicalPassportId(String value) {
        return Long.toString(parsePassportId(value));
    }

    private static long parsePassportId(String value) {
        try {
            String raw = value == null ? "" : value.trim().replaceFirst("^passport-", "");
            if (!raw.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(raw);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException ex) {
            throw new ru.hvostid.common.exception.ValidationException("Passport id must be a positive 64-bit integer");
        }
    }

    private static Long existingPassportId(Listing listing) {
        try {
            return parsePassportId(listing.getPassportId());
        } catch (ru.hvostid.common.exception.ValidationException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String determineRole(Set<String> userRoles, boolean isOwner) {
        if (userRoles.contains(UserRole.ADMIN.value())) return UserRole.ADMIN.value();
        if (userRoles.contains(UserRole.MODERATOR.value())) return UserRole.MODERATOR.value();
        if (isOwner) return "OWNER";
        return "OTHER";
    }

    private void checkForDuplicate(ListingRequest request, Long sellerId) {
        checkForDuplicateTitle(sellerId, normalize(request.title()));
    }

    private void checkForDuplicateTitle(Long sellerId, String title) {
        boolean exists = listingRepository.existsBySellerIdAndTitleIgnoreCaseAndStatusNot(
                sellerId, title, ListingStatus.ARCHIVED);
        if (exists) {
            throw new DuplicateListingException("You already have a listing with this title");
        }
    }

    public static String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
