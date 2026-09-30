package ru.hvostid.listing.service;

import java.sql.Types;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.exception.ConflictException;
import ru.hvostid.listing.dto.ListingFormDraft;

@Service
public class ListingFormService {
    private final JdbcClient jdbc;

    public ListingFormService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Optional<ListingFormDraft> get(long seller, UUID form) {
        var existing = read(seller, form);
        if (existing.isPresent()) return existing;
        // Claim the legacy single form exactly once, preserving saved work during upgrade.
        var legacy = jdbc.sql("SELECT seller_id FROM listing_form_drafts WHERE seller_id=:seller FOR UPDATE")
                .param("seller", seller)
                .query(Long.class)
                .optional();
        if (legacy.isPresent()) {
            int copied = jdbc.sql(
                            "INSERT INTO listing_forms(seller_id,form_id,title,description,species,breed,age,price,city,version) SELECT seller_id,:form,title,description,species,breed,age,price,city,1 FROM listing_form_drafts WHERE seller_id=:seller ON CONFLICT(seller_id,form_id) DO NOTHING")
                    .param("seller", seller)
                    .param("form", form)
                    .update();
            if (copied > 0)
                jdbc.sql("DELETE FROM listing_form_drafts WHERE seller_id=:seller")
                        .param("seller", seller)
                        .update();
        }
        return read(seller, form);
    }

    private Optional<ListingFormDraft> read(long seller, UUID form) {
        return jdbc.sql(
                        "SELECT title,description,species,breed,age,price,city,passport_id,version FROM listing_forms WHERE seller_id=:seller AND form_id=:form")
                .param("seller", seller)
                .param("form", form)
                .query(ListingFormDraft.class)
                .optional();
    }

    @Transactional
    public ListingFormDraft save(long seller, UUID form, ListingFormDraft draft) {
        int updated = jdbc.sql("""
   INSERT INTO listing_forms(seller_id,form_id,title,description,species,breed,age,price,city,passport_id,version)
   SELECT :seller,:form,:title,:description,:species,:breed,:age,:price,:city,:passport,1 WHERE :version=0
   ON CONFLICT(seller_id,form_id) DO NOTHING
   """)
                .param("seller", seller)
                .param("form", form)
                .param("title", draft.title(), Types.VARCHAR)
                .param("description", draft.description(), Types.VARCHAR)
                .param("species", draft.species(), Types.VARCHAR)
                .param("breed", draft.breed(), Types.VARCHAR)
                .param("age", draft.age(), Types.INTEGER)
                .param("price", draft.price(), Types.INTEGER)
                .param("city", draft.city(), Types.VARCHAR)
                .param("passport", draft.passportId(), Types.VARCHAR)
                .param("version", draft.version())
                .update();
        if (draft.version() > 0)
            updated = jdbc.sql(
                            "UPDATE listing_forms SET title=:title,description=:description,species=:species,breed=:breed,age=:age,price=:price,city=:city,passport_id=:passport,version=version+1,updated_at=NOW() WHERE seller_id=:seller AND form_id=:form AND version=:version")
                    .param("seller", seller)
                    .param("form", form)
                    .param("title", draft.title(), Types.VARCHAR)
                    .param("description", draft.description(), Types.VARCHAR)
                    .param("species", draft.species(), Types.VARCHAR)
                    .param("breed", draft.breed(), Types.VARCHAR)
                    .param("age", draft.age(), Types.INTEGER)
                    .param("price", draft.price(), Types.INTEGER)
                    .param("city", draft.city(), Types.VARCHAR)
                    .param("passport", draft.passportId(), Types.VARCHAR)
                    .param("version", draft.version())
                    .update();
        if (updated == 0) throw new ConflictException("This form was changed in another tab; reload it before saving");
        return draft.nextVersion();
    }

    @Transactional
    public void delete(long seller, UUID form, long version) {
        int deleted = jdbc.sql(
                        "DELETE FROM listing_forms WHERE seller_id=:seller AND form_id=:form AND version=:version")
                .param("seller", seller)
                .param("form", form)
                .param("version", version)
                .update();
        if (deleted == 0 && read(seller, form).isPresent())
            throw new ConflictException("This form was changed in another tab; its newer version was preserved");
    }

    @Scheduled(fixedDelayString = "${hvostid.drafts.cleanup-delay-ms:86400000}")
    public void cleanup() {
        jdbc.sql("DELETE FROM listing_forms WHERE updated_at<NOW()-INTERVAL '90 days'")
                .update();
    }
}
