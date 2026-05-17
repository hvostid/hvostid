package ru.hvostid.listing.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.ListingFlag;

public interface ListingFlagRepository extends JpaRepository<ListingFlag, Long> {
    boolean existsByListingIdAndReporterId(Long listingId, Long reporterId);

    long countByListingIdAndStatus(Long listingId, FlagStatus status);

    Page<ListingFlag> findByStatus(FlagStatus status, Pageable pageable);

    List<ListingFlag> findByListingIdOrderByCreatedAtDesc(Long listingId);
}
