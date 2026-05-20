package ru.hvostid.listing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.hvostid.listing.entity.ListingStatusHistory;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, Long> {
    @Modifying
    @Query("DELETE FROM ListingStatusHistory h WHERE h.listingId = :listingId")
    void deleteByListingId(Long listingId);

    long countByListingId(Long listingId);
}
