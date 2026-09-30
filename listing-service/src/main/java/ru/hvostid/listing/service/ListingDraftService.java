package ru.hvostid.listing.service;

import java.sql.Types;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.ListingDraftRequest;

@Service
public class ListingDraftService {
    private final JdbcClient jdbcClient;

    public ListingDraftService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public Optional<ListingDraftRequest> getDraft(Long sellerId) {
        return jdbcClient
                .sql("SELECT title, description, species, breed, age, price, city "
                        + "FROM listing_form_drafts WHERE seller_id = :sellerId")
                .param("sellerId", sellerId)
                .query(ListingDraftRequest.class)
                .optional();
    }

    @Transactional
    public void saveDraft(Long sellerId, ListingDraftRequest draft) {
        jdbcClient
                .sql("""
                INSERT INTO listing_form_drafts (seller_id, title, description, species, breed, age, price, city)
                VALUES (:sellerId, :title, :description, :species, :breed, :age, :price, :city)
                ON CONFLICT (seller_id) DO UPDATE SET
                    title = EXCLUDED.title, description = EXCLUDED.description,
                    species = EXCLUDED.species, breed = EXCLUDED.breed,
                    age = EXCLUDED.age, price = EXCLUDED.price, city = EXCLUDED.city
                """)
                .param("sellerId", sellerId)
                .param("title", draft.title())
                .param("description", draft.description(), Types.VARCHAR)
                .param("species", draft.species())
                .param("breed", draft.breed(), Types.VARCHAR)
                .param("age", draft.age(), Types.INTEGER)
                .param("price", draft.price(), Types.INTEGER)
                .param("city", draft.city())
                .update();
    }

    @Transactional
    public void deleteDraft(Long sellerId) {
        jdbcClient
                .sql("DELETE FROM listing_form_drafts WHERE seller_id = :sellerId")
                .param("sellerId", sellerId)
                .update();
    }
}
