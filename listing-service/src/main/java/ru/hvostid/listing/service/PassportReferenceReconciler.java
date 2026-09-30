package ru.hvostid.listing.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hvostid.listing.client.PassportServiceClient;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@Service
public class PassportReferenceReconciler {
    private final JdbcClient jdbc;
    private final ListingRepository listings;
    private final PassportServiceClient passports;
    private final TransactionTemplate tx;

    public PassportReferenceReconciler(
            JdbcClient jdbc,
            ListingRepository listings,
            PassportServiceClient passports,
            PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.listings = listings;
        this.passports = passports;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${hvostid.references.retry-delay-ms:10000}")
    public void retry() {
        for (Long id : jdbc.sql(
                        "SELECT id FROM passport_reference_jobs WHERE available_at<=NOW() ORDER BY available_at LIMIT 100")
                .query(Long.class)
                .list()) {
            try {
                tx.executeWithoutResult(status -> reconcile(id));
            } catch (RuntimeException ex) {
                jdbc.sql(
                                "UPDATE passport_reference_jobs SET attempts=attempts+1,available_at=NOW()+INTERVAL '1 minute' WHERE id=:id")
                        .param("id", id)
                        .update();
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Passport reference retry failed jobId={}", id);
            }
        }
    }

    private void reconcile(Long id) {
        // Lock order matches listing writes. Never release an acquired reference while its transition is uncommitted.
        var listingId = jdbc.sql("SELECT listing_id FROM passport_reference_jobs WHERE id=:id")
                .param("id", id)
                .query(Long.class)
                .optional();
        if (listingId.isEmpty()) return;
        var listing = listings.findLockedById(listingId.get());
        var job = jdbc.sql("SELECT passport_id,seller_id FROM passport_reference_jobs WHERE id=:id FOR UPDATE")
                .param("id", id)
                .query(Job.class)
                .optional();
        if (job.isEmpty()) return;
        if (listing.isPresent()
                && (listing.get().getStatus() == ListingStatus.MODERATION
                        || listing.get().getStatus() == ListingStatus.PUBLISHED))
            passports.acquire(
                    job.get().passportId(),
                    listingId.get(),
                    job.get().sellerId(),
                    listing.get().getStatus() == ListingStatus.PUBLISHED,
                    id * 2 + 1);
        else passports.release(job.get().passportId(), listingId.get(), id * 2 + 1);
        jdbc.sql("DELETE FROM passport_reference_jobs WHERE id=:id")
                .param("id", id)
                .update();
    }

    record Job(long passportId, long sellerId) {}
}
