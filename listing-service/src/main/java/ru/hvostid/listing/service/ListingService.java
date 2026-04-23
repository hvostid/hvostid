package ru.hvostid.listing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.*;
import ru.hvostid.listing.repository.ListingRepository;

@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);

    private final ListingRepository listingRepository;

    public ListingService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
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
                normalize(request.passportId())
        );

        // репозиторий сохраняет entity
        Listing saved = listingRepository.save(listing);
        log.info("Listing created id={} sellerId={}", saved.getId(), saved.getSellerId());

        // сервис маппит entity в dto ответа
        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ListingResponse getListing(Long id, Long userId) {
        log.debug("Getting listing id={} for userId={}", id, userId);

        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + id));

        boolean isOwner = listing.getSellerId().equals(userId);
        boolean isPublished = listing.getStatus() == ListingStatus.PUBLISHED;

        if (!isPublished && !isOwner) {
            log.warn("Access denied to listing id={} for userId={}, status={}",
                    id, userId, listing.getStatus());
            throw new AccessDeniedException("You don't have permission to view this listing");
        }

        return ListingResponse.from(listing);
    }

    @Transactional
    public ListingResponse updateListing(Long id, ListingUpdateRequest request, Long userId) {
        log.debug("Updating listing id={} for userId={}", id, userId);

        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ListingNotFoundException("Listing not found with id: " + id));

        if (!listing.getSellerId().equals(userId)) {
            log.warn("Update denied: not owner listingId={} userId={}", id, userId);
            throw new AccessDeniedException("You don't have permission to edit this listing");
        }

        if (listing.getStatus() != ListingStatus.DRAFT &&
                listing.getStatus() != ListingStatus.PUBLISHED &&
                listing.getStatus() != ListingStatus.REJECTED) {
            throw new InvalidListingStatusException(
                    "Cannot edit listing in status: " + listing.getStatus() +
                            ". Only DRAFT/PUBLISHED/REJECTED listings can be edited."
            );
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
        return listingRepository.findByStatus(ListingStatus.PUBLISHED, pageable)
                .map(ListingResponse::from);
    }

    private void checkForDuplicate(ListingRequest request, Long sellerId) {
        boolean exists = listingRepository.existsBySellerIdAndTitleAndStatusNot(
                sellerId,
                normalize(request.title()),
                ListingStatus.ARCHIVED
        );
        if (exists) {
            throw new DuplicateListingException("You already have a listing with this title");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
