package ru.hvostid.listing.repository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.ListingFlag;

public interface ListingFlagRepository extends JpaRepository<ListingFlag, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from ListingFlag f where f.id=:id")
    java.util.Optional<ListingFlag> findLockedById(Long id);

    boolean existsByListingIdAndReporterId(Long listingId, Long reporterId);

    long countByListingIdAndStatus(Long listingId, FlagStatus status);

    long countByListingId(Long listingId);

    Page<ListingFlag> findByStatus(FlagStatus status, Pageable pageable);

    /**
     * Most recent flags first, bounded to the latest 50 so the moderation detail view
     * does not pull an unbounded list for pathologically flagged listings.
     */
    List<ListingFlag> findTop50ByListingIdOrderByCreatedAtDesc(Long listingId);

    @Modifying
    @Query("DELETE FROM ListingFlag f WHERE f.listingId = :listingId")
    void deleteByListingId(@Param("listingId") Long listingId);
}
