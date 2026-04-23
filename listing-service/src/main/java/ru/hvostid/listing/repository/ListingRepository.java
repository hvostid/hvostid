package ru.hvostid.listing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);
    boolean existsBySellerIdAndTitleAndStatusNot(Long sellerId, String title, ListingStatus status);
}