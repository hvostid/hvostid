package ru.hvostid.listing.service;

import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

        Listing saved = listingRepository.save(listing);
        log.info("Listing created id={} sellerId={}", saved.getId(), saved.getSellerId());

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
            throw new InvalidStatusTransitionException(
                    String.format("Comment is required for transition from %s to %s", oldStatus, newStatus));
        }

        String normalizedComment =
                (request.comment() != null && !request.comment().isBlank()) ? request.comment() : null;
        listing.setModerationComment(normalizedComment);

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

    @Transactional(readOnly = true)
    public Page<ListingResponse> searchListings(String keyword, Pageable pageable) {
        log.debug(
                "Searching listings with keyword='{}', page={}, size={}",
                keyword,
                pageable.getPageNumber(),
                pageable.getPageSize());

        if (keyword == null || keyword.isBlank() || "\"\"".equals(keyword.trim())) {
            return getPublishedListings(pageable);
        }

        String sanitizedKeyword = normalizeKeyword(keyword);

        return listingRepository
                .searchByKeyword(ListingStatus.PUBLISHED.name(), sanitizedKeyword, pageable)
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

        Pageable safePageable = pageable;
        if (pageable.getPageSize() > ListingConstants.MAX_SEARCH_RESULTS) {
            safePageable =
                    PageRequest.of(pageable.getPageNumber(), ListingConstants.MAX_SEARCH_RESULTS, pageable.getSort());
        }

        if (safePageable.getOffset() >= ListingConstants.MAX_SEARCH_RESULTS) {
            return Page.empty(safePageable);
        }

        Page<Listing> searchResults =
                listingRepository.searchByKeyword(ListingStatus.PUBLISHED.name(), sanitizedKeyword, safePageable);

        if (filters == null || filters.isEmpty()) {
            return searchResults.map(ListingResponse::from);
        }

        // Apply filters in-memory
        List<ListingResponse> filtered = searchResults.getContent().stream()
                .filter(listing -> matchesFilters(listing, filters))
                .map(ListingResponse::from)
                .toList();

        return new PageImpl<>(filtered, safePageable, searchResults.getTotalElements());
    }

    private boolean matchesFilters(Listing listing, ListingFilterRequest filters) {
        if (filters == null) {
            return true;
        }

        if (filters.species() != null && !filters.species().isBlank()) {
            String species = listing.getSpecies();
            if (species == null
                    || !species.toLowerCase().contains(filters.species().toLowerCase())) {
                return false;
            }
        }

        if (filters.breed() != null && !filters.breed().isBlank()) {
            String breed = listing.getBreed();
            if (breed == null || !breed.toLowerCase().contains(filters.breed().toLowerCase())) {
                return false;
            }
        }

        if (filters.city() != null && !filters.city().isBlank()) {
            String city = listing.getCity();
            if (city == null || !city.equalsIgnoreCase(filters.city())) {
                return false;
            }
        }

        if (filters.ageMin() != null || filters.ageMax() != null) {
            Integer age = listing.getAge();
            if (age == null) {
                return false;
            }
            if (filters.ageMin() != null && age < filters.ageMin()) {
                return false;
            }
            if (filters.ageMax() != null && age > filters.ageMax()) {
                return false;
            }
        }

        if (filters.priceMin() != null || filters.priceMax() != null) {
            Integer price = listing.getPrice();
            if (price == null) {
                return false;
            }
            if (filters.priceMin() != null && price < filters.priceMin()) {
                return false;
            }
            return filters.priceMax() == null || price <= filters.priceMax();
        }

        return true;
    }

    private String determineRole(Set<String> userRoles, boolean isOwner) {
        if (userRoles.contains(UserRole.ADMIN.value())) return UserRole.ADMIN.value();
        if (userRoles.contains(UserRole.MODERATOR.value())) return UserRole.MODERATOR.value();
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

    public static String normalizeKeyword(String keyword) {
        return keyword == null ? null : keyword.trim().replaceAll("\\s+", " ");
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
