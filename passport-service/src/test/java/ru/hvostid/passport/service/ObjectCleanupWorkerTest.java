package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.hvostid.passport.AbstractPassportIntegrationTest;

@SpringBootTest
class ObjectCleanupWorkerTest extends AbstractPassportIntegrationTest {
    @Autowired
    private ObjectCleanupService jobs;

    @Autowired
    private ObjectCleanupWorker worker;

    @Autowired
    private JdbcClient jdbc;

    @MockitoBean
    private MinioClient minioClient;

    @Test
    void storageFailureRemainsDurableAndSucceedsOnRetry() throws Exception {
        String object = "retry/" + UUID.randomUUID();
        jobs.enqueue("pet-documents", object);
        doThrow(new IllegalStateException("storage temporarily unavailable"))
                .when(minioClient)
                .removeObject(any(RemoveObjectArgs.class));
        worker.retry();
        assertThat(jdbc.sql("SELECT attempts FROM object_cleanup_jobs WHERE storage_path=:path")
                        .param("path", object)
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));
        jdbc.sql("UPDATE object_cleanup_jobs SET available_at=NOW() WHERE storage_path=:path")
                .param("path", object)
                .update();
        worker.retry();
        assertThat(jdbc.sql("SELECT count(*) FROM object_cleanup_jobs WHERE storage_path=:path")
                        .param("path", object)
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
