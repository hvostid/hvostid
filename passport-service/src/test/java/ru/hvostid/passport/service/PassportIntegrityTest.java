package ru.hvostid.passport.service;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hvostid.passport.AbstractPassportIntegrationTest;
import ru.hvostid.passport.TestDocumentContent;
import ru.hvostid.passport.client.ListingServiceClient;
import ru.hvostid.passport.config.MinioProperties;
import ru.hvostid.passport.dto.CreatePassportRequest;
import ru.hvostid.passport.dto.UpdatePassportRequest;
import ru.hvostid.passport.dto.VaccinationRequest;
import ru.hvostid.passport.entity.Gender;
import ru.hvostid.passport.entity.PassportDocumentType;
import ru.hvostid.passport.exception.PassportInUseException;
import ru.hvostid.passport.repository.PassportDocumentRepository;
import ru.hvostid.passport.repository.PetPassportRepository;
import ru.hvostid.passport.storage.MinioStorageException;
import ru.hvostid.passport.storage.MinioStorageService;

@SpringBootTest
class PassportIntegrityTest extends AbstractPassportIntegrationTest {
    @Autowired
    private PassportService passports;

    @Autowired
    private PassportReferenceService references;

    @Autowired
    private PassportDocumentService documents;

    @Autowired
    private PassportDocumentRepository documentRepository;

    @Autowired
    private PetPassportRepository repository;

    @Autowired
    private ObjectCleanupWorker cleanup;

    @Autowired
    private MinioStorageService storage;

    @Autowired
    private MinioProperties minio;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager manager;

    @Autowired
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockitoBean
    private ListingServiceClient listingServiceClient;

    private long create(List<VaccinationRequest> vaccinations) {
        return passports
                .createPassport(
                        new CreatePassportRequest(
                                "dog",
                                "Mixed",
                                "Rex",
                                LocalDate.of(2023, 1, 1),
                                Gender.MALE,
                                "brown",
                                "calm",
                                null,
                                true,
                                true,
                                vaccinations),
                        4242L)
                .id();
    }

    @Test
    void vaccinationsPersistAndAnEmptyArrayClearsThem() {
        var vaccination = new VaccinationRequest("Rabies", LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        long id = create(List.of(vaccination));
        var saved = passports.getPassport(id, 4242L, Set.of());
        assertThat(saved.vaccinations()).hasSize(1);
        assertThat(saved.vaccinations().getFirst().nextDate()).isEqualTo(vaccination.nextDate());
        assertThat(saved.vaccinations().getFirst().verified()).isFalse();
        passports.updatePassport(
                id, new UpdatePassportRequest(null, null, null, null, null, null, "friendly", null, null, null), 4242L);
        assertThat(passports.getPassport(id, 4242L, Set.of()).vaccinations()).hasSize(1);
        passports.updatePassport(
                id,
                new UpdatePassportRequest(null, null, null, null, null, null, null, null, null, null, List.of()),
                4242L);
        assertThat(passports.getPassport(id, 4242L, Set.of()).vaccinations()).isEmpty();
    }

    @Test
    void changingBirthDateCannotInvalidatePreservedVaccinations() {
        long id = create(List.of(new VaccinationRequest("Rabies", LocalDate.of(2025, 1, 1), null)));
        assertThatThrownBy(() -> passports.updatePassport(
                        id,
                        new UpdatePassportRequest(
                                null, null, null, LocalDate.of(2025, 2, 1), null, null, null, null, null, null),
                        4242L))
                .isInstanceOf(ru.hvostid.common.exception.ValidationException.class);
        assertThat(passports.getPassport(id, 4242L, Set.of()).birthDate()).isEqualTo(LocalDate.of(2023, 1, 1));
        assertThat(passports.getPassport(id, 4242L, Set.of()).vaccinations()).hasSize(1);
    }

    @Test
    void referenceProtectsAgainstEditingDeletingAndCompetingListing() {
        long id = create(null);
        references.acquire(id, 980001L, 4242L, true, 2L);
        assertThat(repository.findById(id).orElseThrow().isModerated()).isTrue();
        assertThatThrownBy(() -> references.acquire(id, 980002L, 4242L, false, 2L))
                .isInstanceOf(PassportInUseException.class);
        assertThatThrownBy(() -> passports.deletePassport(id, 4242L, null)).isInstanceOf(PassportInUseException.class);
        assertThatThrownBy(() -> passports.updatePassport(
                        id,
                        new UpdatePassportRequest(null, null, "Replacement", null, null, null, null, null, null, null),
                        4242L))
                .isInstanceOf(PassportInUseException.class);
        references.release(id, 980001L, 3L);
        passports.updatePassport(
                id,
                new UpdatePassportRequest(null, null, "Replacement", null, null, null, null, null, null, null),
                4242L);
        assertThat(repository.findById(id).orElseThrow().isModerated()).isFalse();
    }

    @Test
    void deletionWaitsForConcurrentReservationAndThenRejectsIt() throws Exception {
        long id = create(null);
        var acquired = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reserve = executor.submit(() -> new TransactionTemplate(manager).executeWithoutResult(status -> {
                references.acquire(id, 980003L, 4242L, false, 2L);
                acquired.countDown();
                try {
                    assertThat(commit.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
            var delete = executor.submit(() -> catchThrowable(() -> passports.deletePassport(id, 4242L, null)));
            commit.countDown();
            reserve.get(10, TimeUnit.SECONDS);
            assertThat(delete.get(10, TimeUnit.SECONDS)).isInstanceOf(PassportInUseException.class);
            assertThat(repository.existsById(id)).isTrue();
        } finally {
            commit.countDown();
        }
    }

    @Test
    void fileDeletionRollbackKeepsObjectAndCommittedDeletionQueuesRetry() throws Exception {
        long id = create(null);
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", TestDocumentContent.image("jpg"));
        long documentId = documents
                .uploadDocument(id, file, PassportDocumentType.PHOTO, 4242L)
                .id();
        String path = documentRepository.findById(documentId).orElseThrow().getStoragePath();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            documents.deleteDocument(id, documentId, 4242L);
            status.setRollbackOnly();
        });
        cleanup.retry();
        assertThat(documentRepository.existsById(documentId)).isTrue();
        try (var stream = storage.download(minio.buckets().photos(), path)) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
        documents.deleteDocument(id, documentId, 4242L);
        assertThat(jdbc.sql("SELECT count(*) FROM object_cleanup_jobs WHERE storage_path=:path")
                        .param("path", path)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        cleanup.retry();
        assertThatThrownBy(() -> storage.download(minio.buckets().photos(), path))
                .isInstanceOf(MinioStorageException.class);
        assertThat(jdbc.sql("SELECT count(*) FROM object_cleanup_jobs WHERE storage_path=:path")
                        .param("path", path)
                        .query(Long.class)
                        .single())
                .isZero();
    }

    @Test
    void pageOfOneHundredPassportsUsesBoundedQueries() {
        for (int index = 0; index < 100; index++) {
            passports.createPassport(
                    new CreatePassportRequest(
                            "dog",
                            "Mixed",
                            "Batch " + index,
                            LocalDate.of(2023, 1, 1),
                            Gender.MALE,
                            "brown",
                            null,
                            null,
                            false,
                            false,
                            List.of(new VaccinationRequest("Rabies", LocalDate.of(2025, 1, 1), null))),
                    7777L);
        }
        var statistics =
                entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            long before = statistics.getPrepareStatementCount();
            var page = passports.getMyPassports(7777L, org.springframework.data.domain.PageRequest.of(0, 100));
            assertThat(page.getContent())
                    .hasSize(100)
                    .allSatisfy(passport -> assertThat(passport.vaccinations()).hasSize(1));
            assertThat(statistics.getPrepareStatementCount() - before).isLessThanOrEqualTo(5);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    @Test
    void lateAcquisitionCannotResurrectCompensatedReservation() {
        long id = create(null);
        references.release(id, 980004L, 3L);
        references.acquire(id, 980004L, 4242L, true, 2L);
        assertThat(repository.findById(id).orElseThrow().isModerated()).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM passport_listing_references WHERE passport_id=:id")
                        .param("id", id)
                        .query(Long.class)
                        .single())
                .isZero();
        references.acquire(id, 980004L, 4242L, false, 4L);
        assertThatThrownBy(() -> passports.deletePassport(id, 4242L, null)).isInstanceOf(PassportInUseException.class);
        references.release(id, 980004L, 5L);
        passports.deletePassport(id, 4242L, null);
        assertThat(repository.existsById(id)).isFalse();
    }
}
