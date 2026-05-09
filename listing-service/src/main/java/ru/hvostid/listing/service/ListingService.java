package ru.hvostid.listing.service;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.entity.ListingStatusHistory;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.DuplicateListingException;
import ru.hvostid.listing.exception.InvalidListingStatusException;
import ru.hvostid.listing.exception.ListingNotFoundException;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

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

        Listing listing = new Listing(
                sellerId,
                normalize(request.title()),
                normalize(request.description()),
                normalize(request.species()),
                normalize(request.breed()),
                request.age(),
                request.price(),
                normalize(request.city()),
                normalize(request.passportId()));

        // Persist the listing entity.
        Listing saved = listingRepository.save(listing);
        log.info("Listing created id={} sellerId={}", saved.getId(), saved.getSellerId());

        // Map the entity to the response DTO.
        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ListingResponse getListing(Long id, Long userId) {
        log.debug("Getting listing id={} for userId={}", id, userId);

        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + id));

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
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + id));

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
        return listingRepository.findByStatus(ListingStatus.PUBLISHED, pageable).map(ListingResponse::from);
    }

    @Transactional
    public ListingResponse updateStatus(Long id, StatusUpdateRequest request, Long userId, Set<String> userRoles) {
        log.debug("Updating status listingId={} to {} by userId={}, roles={}", id, request.status(), userId, userRoles);

        Listing listing = listingRepository
                .findById(id)
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + id));

        boolean isOwner = listing.getSellerId().equals(userId);
        ListingStatus oldStatus = listing.getStatus();
        ListingStatus newStatus = request.status();

        StatusTransition transition = StatusTransitionValidator.validateTransition(oldStatus, newStatus);

        StatusTransitionValidator.checkPermissions(transition, isOwner, userRoles);

        if (StatusTransitionValidator.isCommentRequired(transition)
                && (request.comment() == null || request.comment().isBlank())) {
            throw new InvalidListingStatusException(
                    String.format("Comment is required for transition from %s to %s", oldStatus, newStatus));
        }

        if (request.comment() != null && !request.comment().isBlank()) {
            listing.setModerationComment(request.comment());
        } else {
            listing.setModerationComment(null);
        }

        listing.setStatus(newStatus);
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

    private String determineRole(Set<String> userRoles, boolean isOwner) {
        if (userRoles.contains("ADMIN")) return "ADMIN";
        if (userRoles.contains("MODERATOR")) return "MODERATOR";
        if (isOwner) return "OWNER";
        return "OTHER";
    }

    private void checkForDuplicate(ListingRequest request, Long sellerId) {
        boolean exists = listingRepository.existsBySellerIdAndTitleAndStatusNot(
                sellerId, normalize(request.title()), ListingStatus.ARCHIVED);
        if (exists) {
            throw new DuplicateListingException("You already have a listing with this title");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
