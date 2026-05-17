package ru.hvostid.listing.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;

public interface ListingRepository extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {
    Page<Listing> findByStatus(ListingStatus status, Pageable pageable);

    Page<Listing> findBySellerId(Long sellerId, Pageable pageable);

    Page<Listing> findBySellerIdAndStatus(Long sellerId, ListingStatus status, Pageable pageable);

    boolean existsBySellerIdAndTitleAndStatusNot(Long sellerId, String title, ListingStatus status);

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
    AND (CAST(:species AS TEXT) IS NULL OR l.species ILIKE '%' || CAST(:species AS TEXT) || '%')
    AND (CAST(:breed AS TEXT) IS NULL OR l.breed ILIKE '%' || CAST(:breed AS TEXT) || '%')
    AND (CAST(:ageMin AS INTEGER) IS NULL OR l.age >= CAST(:ageMin AS INTEGER))
    AND (CAST(:ageMax AS INTEGER) IS NULL OR l.age <= CAST(:ageMax AS INTEGER))
    AND (CAST(:priceMin AS INTEGER) IS NULL OR l.price >= CAST(:priceMin AS INTEGER))
    AND (CAST(:priceMax AS INTEGER) IS NULL OR l.price <= CAST(:priceMax AS INTEGER))
    AND (CAST(:city AS TEXT) IS NULL OR LOWER(l.city) = LOWER(CAST(:city AS TEXT)))
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
    AND (CAST(:species AS TEXT) IS NULL OR l.species ILIKE '%' || CAST(:species AS TEXT) || '%')
    AND (CAST(:breed AS TEXT) IS NULL OR l.breed ILIKE '%' || CAST(:breed AS TEXT) || '%')
    AND (CAST(:ageMin AS INTEGER) IS NULL OR l.age >= CAST(:ageMin AS INTEGER))
    AND (CAST(:ageMax AS INTEGER) IS NULL OR l.age <= CAST(:ageMax AS INTEGER))
    AND (CAST(:priceMin AS INTEGER) IS NULL OR l.price >= CAST(:priceMin AS INTEGER))
    AND (CAST(:priceMax AS INTEGER) IS NULL OR l.price <= CAST(:priceMax AS INTEGER))
    AND (CAST(:city AS TEXT) IS NULL OR LOWER(l.city) = LOWER(CAST(:city AS TEXT)))
    """, nativeQuery = true)
    Page<Listing> searchByKeyword(
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("species") String species,
            @Param("breed") String breed,
            @Param("ageMin") Integer ageMin,
            @Param("ageMax") Integer ageMax,
            @Param("priceMin") Integer priceMin,
            @Param("priceMax") Integer priceMax,
            @Param("city") String city,
            Pageable pageable);
}
