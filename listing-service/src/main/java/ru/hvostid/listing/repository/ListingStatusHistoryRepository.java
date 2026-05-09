package ru.hvostid.listing.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.hvostid.listing.entity.ListingStatusHistory;

public interface ListingStatusHistoryRepository extends JpaRepository<ListingStatusHistory, Long> {
}
