package ru.hvostid.listing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.ListingFlag;

public interface ListingFlagRepository extends JpaRepository<ListingFlag, Long> {
    boolean existsByListingIdAndReporterId(Long listingId, Long reporterId);

    long countByListingIdAndStatus(Long listingId, FlagStatus status);
}
