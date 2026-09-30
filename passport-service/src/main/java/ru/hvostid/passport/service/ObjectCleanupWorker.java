package ru.hvostid.passport.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hvostid.passport.storage.MinioStorageService;

@Service
public class ObjectCleanupWorker {
    private final JdbcClient jdbc;
    private final MinioStorageService storage;
    private final TransactionTemplate tx;

    public ObjectCleanupWorker(JdbcClient jdbc, MinioStorageService storage, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.tx = new TransactionTemplate(manager);
    }

    @Scheduled(fixedDelayString = "${hvostid.storage.cleanup-delay-ms:30000}")
    public void retry() {
        for (Long id : jdbc.sql(
                        "SELECT id FROM object_cleanup_jobs WHERE available_at<=NOW() ORDER BY available_at LIMIT 100")
                .query(Long.class)
                .list()) {
            try {
                tx.executeWithoutResult(status -> process(id));
            } catch (RuntimeException ex) {
                jdbc.sql(
                                "UPDATE object_cleanup_jobs SET attempts=attempts+1,available_at=NOW()+INTERVAL '1 minute' WHERE id=:id")
                        .param("id", id)
                        .update();
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Object cleanup will be retried jobId={}", id);
            }
        }
    }

    private void process(long id) {
        var passport = jdbc.sql("SELECT passport_id FROM object_cleanup_jobs WHERE id=:id AND passport_id IS NOT NULL")
                .param("id", id)
                .query(Long.class)
                .optional();
        // Upload metadata and cleanup use the same passport -> job lock order.
        passport.ifPresent(passportId -> jdbc.sql("SELECT id FROM pet_passports WHERE id=:id FOR UPDATE")
                .param("id", passportId)
                .query(Long.class)
                .optional());
        var job = jdbc.sql(
                        "SELECT bucket,storage_path FROM object_cleanup_jobs WHERE id=:id AND available_at<=NOW() FOR UPDATE SKIP LOCKED")
                .param("id", id)
                .query(Job.class)
                .optional();
        if (job.isEmpty()) return;
        if (jdbc.sql("SELECT count(*) FROM passport_documents WHERE storage_path=:path")
                        .param("path", job.get().storagePath())
                        .query(Long.class)
                        .single()
                == 0) storage.delete(job.get().bucket(), job.get().storagePath());
        jdbc.sql("DELETE FROM object_cleanup_jobs WHERE id=:id").param("id", id).update();
    }

    record Job(String bucket, String storagePath) {}
}
