package ru.hvostid.listing.repository;

import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hvostid.listing.dto.ListingFilterRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Listing l where l.id = :id")
    java.util.Optional<Listing> findLockedById(Long id);

    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findBySellerId(Long sellerId, Pageable pageable);

    Page<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status, Pageable pageable);

    boolean existsBySellerIdAndTitleIgnoreCaseAndStatusNot(Long sellerId, String title, ListingStatus status);

    boolean existsByPassportIdInAndStatusIn(Collection<String> passportIds, Collection<ListingStatus> statuses);

    boolean existsByPassportIdInAndStatusAndSellerId(
            Collection<String> passportIds, ListingStatus status, Long sellerId);

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
            AND (CAST(:#{#filter.species()} AS TEXT) IS NULL OR l.species ILIKE '%' || CAST(:#{#filter.species()} AS TEXT) || '%')
            AND (CAST(:#{#filter.breed()} AS TEXT) IS NULL OR l.breed ILIKE '%' || CAST(:#{#filter.breed()} AS TEXT) || '%')
            AND (CAST(:#{#filter.ageMin()} AS INTEGER) IS NULL OR l.age >= CAST(:#{#filter.ageMin()} AS INTEGER))
            AND (CAST(:#{#filter.ageMax()} AS INTEGER) IS NULL OR l.age <= CAST(:#{#filter.ageMax()} AS INTEGER))
            AND (CAST(:#{#filter.priceMin()} AS INTEGER) IS NULL OR l.price >= CAST(:#{#filter.priceMin()} AS INTEGER))
            AND (CAST(:#{#filter.priceMax()} AS INTEGER) IS NULL OR l.price <= CAST(:#{#filter.priceMax()} AS INTEGER))
            AND (CAST(:#{#filter.city()} AS TEXT) IS NULL OR LOWER(l.city) = LOWER(CAST(:#{#filter.city()} AS TEXT)))
            ORDER BY GREATEST(
                ts_rank(l.search_vector_ru, plainto_tsquery('russian', :keyword)),
                ts_rank(l.search_vector_en, plainto_tsquery('simple', :keyword))
            ) DESC, l.id DESC
            """, countQuery = """
            SELECT count(*) FROM listings l
            WHERE l.status = :status
            AND (
                l.search_vector_ru @@ plainto_tsquery('russian', :keyword)
                OR l.search_vector_en @@ plainto_tsquery('simple', :keyword)
            )
            AND (CAST(:#{#filter.species()} AS TEXT) IS NULL OR l.species ILIKE '%' || CAST(:#{#filter.species()} AS TEXT) || '%')
            AND (CAST(:#{#filter.breed()} AS TEXT) IS NULL OR l.breed ILIKE '%' || CAST(:#{#filter.breed()} AS TEXT) || '%')
            AND (CAST(:#{#filter.ageMin()} AS INTEGER) IS NULL OR l.age >= CAST(:#{#filter.ageMin()} AS INTEGER))
            AND (CAST(:#{#filter.ageMax()} AS INTEGER) IS NULL OR l.age <= CAST(:#{#filter.ageMax()} AS INTEGER))
            AND (CAST(:#{#filter.priceMin()} AS INTEGER) IS NULL OR l.price >= CAST(:#{#filter.priceMin()} AS INTEGER))
            AND (CAST(:#{#filter.priceMax()} AS INTEGER) IS NULL OR l.price <= CAST(:#{#filter.priceMax()} AS INTEGER))
            AND (CAST(:#{#filter.city()} AS TEXT) IS NULL OR LOWER(l.city) = LOWER(CAST(:#{#filter.city()} AS TEXT)))
            """, nativeQuery = true)
    Page<Listing> searchByKeyword(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("filter") ListingFilterRequest filter,
            Pageable pageable);
}
