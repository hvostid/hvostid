package ru.hvostid.passport.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.passport.entity.PetPassport;
import ru.hvostid.passport.exception.PassportInUseException;
import ru.hvostid.passport.exception.PassportNotFoundException;
import ru.hvostid.passport.repository.PetPassportRepository;

@Service
public class PassportReferenceService {
    private final PetPassportRepository passports;
    private final PassportAccessService access;
    private final JdbcClient jdbc;
    private final TrustScoreService trust;

    public PassportReferenceService(
            PetPassportRepository passports, PassportAccessService access, JdbcClient jdbc, TrustScoreService trust) {
        this.passports = passports;
        this.access = access;
        this.jdbc = jdbc;
        this.trust = trust;
    }

    @Transactional
    public void acquire(long passportId, long listingId, long sellerId, boolean approved, long revision) {
        PetPassport p = lock(passportId);
        access.requireOwner(p, sellerId, "publish");
        if (!advanceRevision(listingId, revision)) return;
        Long existing = jdbc.sql("SELECT listing_id FROM passport_listing_references WHERE passport_id=:id")
                .param("id", passportId)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (existing != null && existing != listingId)
            throw new PassportInUseException("Passport already belongs to an active listing");
        jdbc.sql(
                        "INSERT INTO passport_listing_references(passport_id,listing_id,approved) VALUES(:id,:listing,:approved) ON CONFLICT(passport_id) DO UPDATE SET approved=EXCLUDED.approved")
                .param("id", passportId)
                .param("listing", listingId)
                .param("approved", approved)
                .update();
        p.setModerated(approved);
        trust.recalculate(passportId);
    }

    @Transactional
    public void release(long passportId, long listingId, long revision) {
        var p = passports.findLockedById(passportId);
        if (!advanceRevision(listingId, revision) || p.isEmpty()) return;
        jdbc.sql("DELETE FROM passport_listing_references WHERE passport_id=:id AND listing_id=:listing")
                .param("id", passportId)
                .param("listing", listingId)
                .update();
    }

    private boolean advanceRevision(long listingId, long revision) {
        return jdbc.sql(
                                "INSERT INTO passport_reference_revisions(listing_id,revision) VALUES(:listing,:revision) ON CONFLICT(listing_id) DO UPDATE SET revision=EXCLUDED.revision WHERE passport_reference_revisions.revision<EXCLUDED.revision")
                        .param("listing", listingId)
                        .param("revision", revision)
                        .update()
                > 0;
    }

    public PetPassport lock(long id) {
        return passports
                .findLockedById(id)
                .orElseThrow(() -> new PassportNotFoundException("Passport not found with id: " + id));
    }

    public void requireUnreferenced(long id) {
        if (jdbc.sql("SELECT count(*) FROM passport_listing_references WHERE passport_id=:id")
                        .param("id", id)
                        .query(Long.class)
                        .single()
                > 0)
            throw new PassportInUseException("Archive the active listing before editing or deleting this passport");
    }
}
