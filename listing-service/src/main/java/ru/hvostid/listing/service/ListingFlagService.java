package ru.hvostid.listing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.FlagListingRequest;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingFlag;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.entity.ListingStatusHistory;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.DuplicateFlagException;
import ru.hvostid.listing.exception.ListingNotFlaggableException;
import ru.hvostid.listing.exception.ListingNotFoundException;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@Service
public class ListingFlagService {
    static final long AUTO_MODERATION_THRESHOLD = 3;
    static final String SYSTEM_ROLE = "SYSTEM";
    static final String AUTO_MODERATION_COMMENT =
            "Auto-moderation: listing reached " + AUTO_MODERATION_THRESHOLD + " pending flags";

    private static final Logger log = LoggerFactory.getLogger(ListingFlagService.class);

    private final ListingRepository listingRepository;
    private final ListingFlagRepository flagRepository;
    private final ListingStatusHistoryRepository historyRepository;

    public ListingFlagService(
            ListingRepository listingRepository,
            ListingFlagRepository flagRepository,
            ListingStatusHistoryRepository historyRepository) {
        this.listingRepository = listingRepository;
        this.flagRepository = flagRepository;
        this.historyRepository = historyRepository;
    }

    @Transactional
    public FlagListingResponse flagListing(Long listingId, FlagListingRequest request, Long reporterId) {
        log.debug("Flagging listingId={} by reporterId={} reason={}", listingId, reporterId, request.reason());

        Listing listing = listingRepository
                .findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + listingId));

        if (listing.getSellerId().equals(reporterId)) {
            throw new AccessDeniedException("You cannot flag your own listing");
        }

        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw new ListingNotFlaggableException(
                    "Only published listings can be flagged. Current status: " + listing.getStatus());
        }

        if (flagRepository.existsByListingIdAndReporterId(listingId, reporterId)) {
            throw new DuplicateFlagException("You have already flagged this listing");
        }

        ListingFlag flag = new ListingFlag(listingId, reporterId, request.reason(), request.description());
        ListingFlag saved = flagRepository.save(flag);
        log.info("Listing flag created id={} listingId={} reporterId={}", saved.getId(), listingId, reporterId);

        maybeAutoModerate(listing, reporterId);

        return FlagListingResponse.from(saved);
    }

    private void maybeAutoModerate(Listing listing, Long reporterId) {
        long pendingCount = flagRepository.countByListingIdAndStatus(listing.getId(), FlagStatus.PENDING);
        if (pendingCount < AUTO_MODERATION_THRESHOLD || listing.getStatus() != ListingStatus.PUBLISHED) {
            return;
        }

        ListingStatus previous = listing.getStatus();
        listing.setStatus(ListingStatus.MODERATION);
        listingRepository.save(listing);

        ListingStatusHistory history = new ListingStatusHistory(
                listing.getId(), previous, ListingStatus.MODERATION, reporterId, SYSTEM_ROLE, AUTO_MODERATION_COMMENT);
        historyRepository.save(history);

        log.info(
                "Auto-moderation: listingId={} moved from {} to MODERATION after {} flags",
                listing.getId(),
                previous,
                pendingCount);
    }
}
