package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.hvostid.common.exception.ConflictException;
import ru.hvostid.common.exception.ValidationException;
import ru.hvostid.listing.ListingIntegrationTest;
import ru.hvostid.listing.dto.*;
import ru.hvostid.listing.entity.*;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
class ListingIntegrityTest extends ListingIntegrationTest {
    @Autowired
    private ListingService service;

    @Autowired
    private ListingRepository listings;

    @Autowired
    private ListingFlagService flags;

    @Autowired
    private ListingFormService forms;

    @Autowired
    private PassportReferenceReconciler references;

    @Autowired
    private PlatformTransactionManager manager;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    private ListingResponse create(String passport) {
        return service.createListing(
                new ListingRequest("Integrity " + UUID.randomUUID(), null, "dog", null, 1, 0, "Moscow", passport),
                8080L);
    }

    @Test
    void canonicalIdClosesAliasGuardBypassAndOwnershipIsRequired() {
        var listing = create("passport-00042");
        assertThat(listing.passportId()).isEqualTo("42");
        verify(passportServiceClient).validateOwner(42L, 8080L);
        service.updateStatus(
                listing.id(), new StatusUpdateRequest(ListingStatus.MODERATION, null), 8080L, Set.of("SELLER"));
        assertThat(service.hasActiveListingForPassport(42L)).isTrue();
        assertThatThrownBy(() -> create("0")).isInstanceOf(ValidationException.class);
        doThrow(new ConflictException("Not owned")).when(passportServiceClient).validateOwner(43L, 8080L);
        assertThatThrownBy(() -> create("43")).isInstanceOf(ConflictException.class);
    }

    @Test
    void migrationBackfillsLegacyAliasesAndFullBigintRangeWithoutDiscardingDrafts() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .target("6")
                    .load()
                    .migrate();
            jdbc.sql("INSERT INTO " + schema + ".listings(seller_id,title,species,city,status,passport_id) VALUES "
                            + "(1,'Alias','dog','Moscow','PUBLISHED',' passport-00042 '),"
                            + "(1,'Largest','dog','Moscow','MODERATION','9223372036854775807'),"
                            + "(1,'Overflow','dog','Moscow','PUBLISHED','9223372036854775808'),"
                            + "(1,'External','dog','Moscow','PUBLISHED','legacy-id'),"
                            + "(1,'Missing','dog','Moscow','PUBLISHED',NULL),"
                            + "(1,'Zero','dog','Moscow','PUBLISHED','0')")
                    .update();
            jdbc.sql(
                            "INSERT INTO " + schema
                                    + ".listing_form_drafts(seller_id,title,species,city,price) VALUES(1,'Unsaved pet','dog','Moscow',0)")
                    .update();
            org.flywaydb.core.Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            assertThat(jdbc.sql("SELECT passport_id FROM " + schema + ".passport_reference_jobs ORDER BY passport_id")
                            .query(Long.class)
                            .list())
                    .containsExactly(42L, Long.MAX_VALUE);
            assertThat(jdbc.sql("SELECT passport_id FROM " + schema + ".listings WHERE title='Alias'")
                            .query(String.class)
                            .single())
                    .isEqualTo("42");
            assertThat(jdbc.sql("SELECT title FROM " + schema + ".listing_form_drafts WHERE seller_id=1")
                            .query(String.class)
                            .single())
                    .isEqualTo("Unsaved pet");
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS " + schema + " CASCADE").update();
        }
    }

    @Test
    void legacyInvalidReferenceCanBeArchivedAndDeletedButNotPublished() {
        for (String invalid : new String[] {null, "old-external-id", "0", "9223372036854775808"}) {
            Listing row = Listing.builder()
                    .sellerId(8080L)
                    .title("Legacy " + UUID.randomUUID())
                    .species("dog")
                    .city("Moscow")
                    .passportId(invalid)
                    .build();
            row.setStatus(ListingStatus.PUBLISHED);
            long id = listings.saveAndFlush(row).getId();
            service.updateStatus(id, new StatusUpdateRequest(ListingStatus.ARCHIVED, null), 8080L, Set.of("SELLER"));
            service.updateStatus(id, new StatusUpdateRequest(ListingStatus.DRAFT, null), 8080L, Set.of("SELLER"));
            assertThatThrownBy(() -> service.updateStatus(
                            id, new StatusUpdateRequest(ListingStatus.MODERATION, null), 8080L, Set.of("SELLER")))
                    .isInstanceOf(ValidationException.class);
            service.deleteListing(id, 8080L, Set.of("SELLER"));
            assertThat(listings.findById(id)).isEmpty();
        }
    }

    @Test
    void rollbackLeavesDurableReservationCompensation() {
        var listing = create("44");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            service.updateStatus(
                    listing.id(), new StatusUpdateRequest(ListingStatus.MODERATION, null), 8080L, Set.of("SELLER"));
            status.setRollbackOnly();
        });
        assertThat(listings.findById(listing.id()).orElseThrow().getStatus()).isEqualTo(ListingStatus.DRAFT);
        jdbc.sql("UPDATE passport_reference_jobs SET available_at=NOW() WHERE listing_id=:id")
                .param("id", listing.id())
                .update();
        references.retry();
        verify(passportServiceClient).release(eq(44L), eq(listing.id()), anyLong());
    }

    @Test
    void simultaneousFlagsCrossThresholdExactlyOnce() throws Exception {
        var listing = create("45");
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var row = listings.findById(listing.id()).orElseThrow();
            row.setStatus(ListingStatus.PUBLISHED);
        });
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = java.util.stream.LongStream.range(1, 4)
                    .mapToObj(user -> executor.submit(() -> {
                        start.await();
                        return flags.flagListing(
                                listing.id(), new FlagListingRequest(FlagReason.OTHER, "Needs review"), user);
                    }))
                    .toList();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(listings.findById(listing.id()).orElseThrow().getStatus()).isEqualTo(ListingStatus.MODERATION);
        assertThat(jdbc.sql("SELECT count(*) FROM listing_status_history WHERE listing_id=:id")
                        .param("id", listing.id())
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    @Test
    void draftVersionsAndIndependentFormsPreventLostUpdates() {
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        var empty = new ListingFormDraft("", null, "", null, null, null, "", null, 0L);
        var saved = forms.save(8080L, a, empty);
        forms.save(8080L, b, empty);
        assertThat(saved.version()).isEqualTo(1);
        assertThatThrownBy(() -> forms.save(8080L, a, empty)).isInstanceOf(ConflictException.class);
        var newer = forms.save(8080L, a, saved);
        assertThatThrownBy(() -> forms.delete(8080L, a, saved.version())).isInstanceOf(ConflictException.class);
        assertThat(forms.get(8080L, a).orElseThrow().version()).isEqualTo(newer.version());
        forms.delete(8080L, a, newer.version());
        assertThat(forms.get(8080L, b)).isPresent();
        assertThat(forms.get(8081L, b)).isEmpty();
    }

    @Test
    void legacySingleDraftIsClaimedOnceWithoutLosingFields() {
        jdbc.sql(
                        "INSERT INTO listing_form_drafts(seller_id,title,description,species,breed,age,price,city) VALUES(8090,'Saved title','Saved description','cat','Mixed',0,0,'Moscow')")
                .update();
        var id = UUID.randomUUID();
        var restored = forms.get(8090, id).orElseThrow();
        assertThat(restored)
                .isEqualTo(new ListingFormDraft(
                        "Saved title", "Saved description", "cat", "Mixed", 0, 0, "Moscow", null, 1L));
        assertThat(forms.get(8090, UUID.randomUUID())).isEmpty();
        assertThat(forms.get(8090, id)).contains(restored);
        assertThat(jdbc.sql("SELECT count(*) FROM listing_form_drafts WHERE seller_id=8090")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
