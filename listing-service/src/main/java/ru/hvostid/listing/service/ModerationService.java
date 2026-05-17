package ru.hvostid.listing.service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ModeratedListingDetailResponse;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingFlag;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.FlagNotFoundException;
import ru.hvostid.listing.exception.InvalidFlagReviewException;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;

@Service
public class ModerationService {
    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    private final ListingService listingService;
    private final ListingRepository listingRepository;
    private final ListingFlagRepository flagRepository;

    public ModerationService(
            ListingService listingService, ListingRepository listingRepository, ListingFlagRepository flagRepository) {
        this.listingService = listingService;
        this.listingRepository = listingRepository;
        this.flagRepository = flagRepository;
    }

    @Transactional(readOnly = true)
    public Page<ListingResponse> getListingsInModeration(Pageable pageable) {
        log.debug("Getting listings in moderation page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return listingRepository
                .findByStatus(ListingStatus.MODERATION, pageable)
                .map(ListingResponse::from);
    }

    @Transactional(readOnly = true)
    public ModeratedListingDetailResponse getListingDetail(Long listingId) {
        Listing listing = listingService.requireListing(listingId);
        List<FlagListingResponse> flags = flagRepository.findTop50ByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(FlagListingResponse::from)
                .toList();
        return new ModeratedListingDetailResponse(ListingResponse.from(listing), flags);
    }

    /**
     * Approves a listing by transitioning it from MODERATION to PUBLISHED. Delegates
     * to {@link ListingService#updateStatus} so the existing transition rules,
     * permission checks and {@code listing_status_history} write all stay in one
     * place.
     */
    @Transactional
    public ListingResponse approveListing(Long listingId, Long userId, Set<String> userRoles) {
        log.info("Moderator approving listingId={} userId={}", listingId, userId);
        return listingService.updateStatus(
                listingId, new StatusUpdateRequest(ListingStatus.PUBLISHED, null), userId, userRoles);
    }

    /**
     * Returns a listing to DRAFT with a moderator comment. Same delegation as
     * {@link #approveListing}; the underlying validator enforces that a comment
     * is present for MODERATION -> DRAFT transitions.
     */
    @Transactional
    public ListingResponse rejectListing(Long listingId, Long userId, Set<String> userRoles, String comment) {
        log.info(
                "Moderator rejecting listingId={} userId={} commentLength={}",
                listingId,
                userId,
                comment == null ? 0 : comment.length());
        return listingService.updateStatus(
                listingId, new StatusUpdateRequest(ListingStatus.DRAFT, comment), userId, userRoles);
    }

    @Transactional(readOnly = true)
    public Page<FlagListingResponse> getPendingFlags(Pageable pageable) {
        log.debug("Listing pending flags page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return flagRepository.findByStatus(FlagStatus.PENDING, pageable).map(FlagListingResponse::from);
    }

    @Transactional
    public FlagListingResponse reviewFlag(Long flagId, FlagStatus decision) {
        if (decision != FlagStatus.REVIEWED && decision != FlagStatus.DISMISSED) {
            throw new InvalidFlagReviewException("Decision must be REVIEWED or DISMISSED, got " + decision);
        }
        ListingFlag flag = flagRepository
                .findById(flagId)
                .orElseThrow(() -> new FlagNotFoundException("Flag not found with id: " + flagId));
        if (flag.getStatus() != FlagStatus.PENDING) {
            throw new InvalidFlagReviewException(
                    "Only PENDING flags can be reviewed; current status: " + flag.getStatus());
        }
        // Dirty entity is auto-flushed at @Transactional commit; an explicit save is redundant.
        flag.setStatus(decision);
        log.info("Flag id={} listingId={} reviewed -> {}", flagId, flag.getListingId(), decision);
        return FlagListingResponse.from(flag);
    }
}
