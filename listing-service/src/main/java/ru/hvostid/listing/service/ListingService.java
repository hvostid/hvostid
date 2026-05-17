package ru.hvostid.listing.service;

import java.time.Instant;
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
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingSpecifications;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);
    private static final String LISTING_NOT_FOUND_MESSAGE = "Listing not found with id: ";

    private final ListingRepository listingRepository;
    private final ListingStatusHistoryRepository historyRepository;

    public ListingService(ListingRepository listingRepository, ListingStatusHistoryRepository historyRepository) {
        this.listingRepository = listingRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public ListingResponse createListing(ListingRequest request, Long sellerId) {
        log.debug("Creating listing for sellerId={}", sellerId);

        checkForDuplicate(request, sellerId);

        Listing listing = Listing.builder()
                .sellerId(sellerId)
                .title(normalize(request.title()))
                .description(normalize(request.description()))
                .species(normalize(request.species()))
                .breed(normalize(request.breed()))
                .age(request.age())
                .price(request.price())
                .city(normalize(request.city()))
                .passportId(normalize(request.passportId()))
                .build();

        Listing saved = listingRepository.save(listing);
        log.info("Listing created id={} sellerId={}", saved.getId(), saved.getSellerId());

        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ListingResponse getListing(Long id, Long userId) {
        log.debug("Getting listing id={} for userId={}", id, userId);

        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException(LISTING_NOT_FOUND_MESSAGE + id));

        boolean isOwner = listing.getSellerId().equals(userId);
        boolean isPublished = listing.getStatus() == ListingStatus.PUBLISHED;

        if (!isPublished && !isOwner) {
            log.warn("Access denied to listing id={} for userId={}, status={}", id, userId, listing.getStatus());
            throw new AccessDeniedException("You don't have permission to view this listing");
        }

        return ListingResponse.from(listing);
    }

    @Transactional
    public ListingResponse updateListing(Long id, ListingUpdateRequest request, Long userId) {
        log.debug("Updating listing id={} for userId={}", id, userId);

        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException(LISTING_NOT_FOUND_MESSAGE + id));

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

        if (request.title() != null) listing.setTitle(normalize(request.title()));
        if (request.description() != null) listing.setDescription(normalize(request.description()));
        if (request.species() != null) listing.setSpecies(normalize(request.species()));
        if (request.breed() != null) listing.setBreed(normalize(request.breed()));
        if (request.age() != null) listing.setAge(request.age());
        if (request.price() != null) listing.setPrice(request.price());
        if (request.city() != null) listing.setCity(normalize(request.city()));

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

        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException(LISTING_NOT_FOUND_MESSAGE + id));

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

        return listingRepository
                .searchByKeyword(
                        ListingStatus.PUBLISHED.name(),
                        sanitizedKeyword,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        pageable)
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
            return getListingsWithFilters(filters, pageable);
        }

        String sanitizedKeyword = normalizeKeyword(keyword);

        // Deep-pagination cap: refuse to return matches past the configured horizon.
        // Bounded by ListingController (MAX_PAGE_SIZE), so we only need to guard the offset.
        if (pageable.getOffset() >= ListingConstants.MAX_SEARCH_RESULTS) {
            return Page.empty(pageable);
        }

        ListingFilterRequest effective =
                filters == null ? new ListingFilterRequest(null, null, null, null, null, null, null) : filters;

        Page<Listing> searchResults = listingRepository.searchByKeyword(
                ListingStatus.PUBLISHED.name(),
                sanitizedKeyword,
                blankToNull(effective.species()),
                blankToNull(effective.breed()),
                effective.ageMin(),
                effective.ageMax(),
                effective.priceMin(),
                effective.priceMax(),
                blankToNull(effective.city()),
                pageable);

        return searchResults.map(ListingResponse::from);
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
        boolean exists =
                listingRepository.existsBySellerIdAndTitleAndStatusNot(sellerId, title, ListingStatus.ARCHIVED);
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
