package ru.hvostid.listing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findAll(Specification<Listing> spec, Pageable pageable);

    boolean existsBySellerIdAndTitleAndStatusNot(Long sellerId, String title, ListingStatus status);

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
