package ru.hvostid.passport.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ObjectCleanupService {
    private final JdbcClient jdbc;

    public ObjectCleanupService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void prepareUpload(String bucket, String path, long passportId) {
        enqueue(bucket, path, Instant.now().plus(1, ChronoUnit.HOURS));
        jdbc.sql("UPDATE object_cleanup_jobs SET passport_id=:passport WHERE bucket=:bucket AND storage_path=:path")
                .param("passport", passportId)
                .param("bucket", bucket)
                .param("path", path)
                .update();
    }

    @Transactional
    public void enqueue(String bucket, String path) {
        enqueue(bucket, path, Instant.now());
    }

    private void enqueue(String bucket, String path, Instant when) {
        jdbc.sql(
                        "INSERT INTO object_cleanup_jobs(bucket,storage_path,available_at) VALUES(:bucket,:path,:at) ON CONFLICT(bucket,storage_path) DO UPDATE SET available_at=EXCLUDED.available_at")
                .param("bucket", bucket)
                .param("path", path)
                .param("at", java.sql.Timestamp.from(when))
                .update();
    }

    @Transactional
    public void uploadCommitted(String bucket, String path) {
        jdbc.sql("DELETE FROM object_cleanup_jobs WHERE bucket=:bucket AND storage_path=:path")
                .param("bucket", bucket)
                .param("path", path)
                .update();
    }
}
