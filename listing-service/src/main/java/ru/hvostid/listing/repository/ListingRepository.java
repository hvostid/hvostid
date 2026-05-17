package ru.hvostid.listing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long> {
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findBySellerId(Long sellerId, Pageable pageable);

    Page<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status, Pageable pageable);

    boolean existsBySellerIdAndTitleAndStatusNot(Long sellerId, String title, ListingStatus status);

    boolean existsByPassportIdAndStatus(String passportId, ListingStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Listing l SET l.status = :newStatus WHERE l.id = :id AND l.status = :expectedStatus")
    int transitionStatus(
            @Param("id") Long id,
            @Param("expectedStatus") ListingStatus expectedStatus,
            @Param("newStatus") ListingStatus newStatus);

    @Query(value = """
    SELECT * FROM listings l
    WHERE l.status = :status
    AND (
        l.search_vector_ru @@ plainto_tsquery('russian', :keyword)
        OR l.search_vector_en @@ plainto_tsquery('simple', :keyword)
    )
    ORDER BY GREATEST(
        ts_rank(l.search_vector_ru, plainto_tsquery('russian', :keyword)),
        ts_rank(l.search_vector_en, plainto_tsquery('simple', :keyword))
    ) DESC
    """, countQuery = """
    SELECT count(*) FROM listings l
    WHERE l.status = :status
    AND (
        l.search_vector_ru @@ plainto_tsquery('russian', :keyword)
        OR l.search_vector_en @@ plainto_tsquery('simple', :keyword)
    )
    """, nativeQuery = true)
    Page<Listing> searchByKeyword(@Param("status") String status, @Param("keyword") String keyword, Pageable pageable);
}
