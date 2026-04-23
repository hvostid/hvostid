package ru.hvostid.listing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.DuplicateListingException;
import ru.hvostid.listing.exception.InvalidListingStatusException;
import ru.hvostid.listing.exception.ListingNotFoundException;
import ru.hvostid.listing.repository.ListingRepository;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListingServiceTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private ListingRepository listingRepository;

    private ListingRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new ListingRequest(
                "Cute Puppy",
                "Friendly and healthy puppy",
                "dog",
                "Labrador",
                3,
                15000,
                "Moscow",
                "passport-1"
        );
    }

    // ==================== CREATE LISTING TESTS ====================

    @Test
    void createListing_WithSellerRole_ShouldReturnListingWithDraftStatus() {
        // when
        ListingResponse response = listingService.createListing(validRequest, 1L);

        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.sellerId()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Cute Puppy");
        assertThat(response.status()).isEqualTo(ListingStatus.DRAFT);
    }

    @Test
    void createListing_duplicateTitle_shouldThrow() {
        listingService.createListing(validRequest, 1L);

        assertThatThrownBy(() ->
                listingService.createListing(validRequest, 1L)
        ).isInstanceOf(DuplicateListingException.class);
    }

    // ==================== GET LISTING TESTS ====================

    @Test
    void getListing_WhenPublishedAndAnyUser_ShouldReturnListing() {
        // given - create listing as seller 1
        ListingResponse created = listingService.createListing(validRequest, 1L);

        // change status to PUBLISHED (simulate moderator action)
        Listing listing = listingRepository.findById(created.id()).orElseThrow();
        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);

        // when - different user (userId=2) tries to view
        ListingResponse response = listingService.getListing(created.id(), 2L);

        // then
        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.status()).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void getListing_WhenDraftAndOwner_ShouldReturnListing() {
        // given - create listing as seller 1
        ListingResponse created = listingService.createListing(validRequest, 1L);

        // when - same user tries to view
        ListingResponse response = listingService.getListing(created.id(), 1L);

        // then
        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.status()).isEqualTo(ListingStatus.DRAFT);
    }

    @Test
    void getListing_WhenDraftAndNotOwner_ShouldThrowAccessDenied() {
        // given - create listing as seller 1
        ListingResponse created = listingService.createListing(validRequest, 1L);

        // when/then - user 2 tries to view
        assertThatThrownBy(() -> listingService.getListing(created.id(), 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("don't have permission");
    }

    @Test
    void getListing_WhenNotFound_ShouldThrowNotFoundException() {
        assertThatThrownBy(() -> listingService.getListing(999L, 1L))
                .isInstanceOf(ListingNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // ==================== UPDATE LISTING TESTS ====================

    @Test
    void updateListing_WhenOwnerAndDraftStatus_ShouldUpdate() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                "Updated Title", "Updated Description", "cat", "Siamese",
                12, 20000, "Saint Petersburg", "passport-1"
        );

        // when
        ListingResponse updated = listingService.updateListing(created.id(), updateRequest, 1L);

        // then
        assertThat(updated.title()).isEqualTo("Updated Title");
        assertThat(updated.description()).isEqualTo("Updated Description");
        assertThat(updated.species()).isEqualTo("cat");
        assertThat(updated.price()).isEqualTo(20000);
    }

    @Test
    void updateListing_WhenOwnerAndPublishedStatus_ShouldUpdate() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        Listing listing = listingRepository.findById(created.id()).orElseThrow();
        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);

        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                "Updated Published Title", null, null, null, null, null, null, null
        );

        // when
        ListingResponse updated = listingService.updateListing(created.id(), updateRequest, 1L);

        // then
        assertThat(updated.title()).isEqualTo("Updated Published Title");
        assertThat(updated.status()).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void updateListing_WhenNotOwner_ShouldThrowAccessDenied() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                "Hacked Title", null, null, null, null, null, null, null
        );

        // when/then - user 2 tries to update
        assertThatThrownBy(() -> listingService.updateListing(created.id(), updateRequest, 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("don't have permission");
    }

    @Test
    void updateListing_WhenPartialUpdate_ShouldUpdateOnlyProvidedFields() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                null, null, null, null, null, 25000, null, null  // only price updated
        );

        // when
        ListingResponse updated = listingService.updateListing(created.id(), updateRequest, 1L);

        // then
        assertThat(updated.price()).isEqualTo(25000);
        assertThat(updated.title()).isEqualTo("Cute Puppy"); // unchanged
        assertThat(updated.species()).isEqualTo("dog"); // unchanged
    }

    // ==================== GET PUBLISHED LISTINGS TESTS ====================

    @Test
    void getPublishedListings_ShouldReturnOnlyPublishedListings() {
        // given - create 3 listings: 2 published, 1 draft
        ListingRequest request1 = new ListingRequest(
                "Unique Puppy 1", "Description", "dog", "Labrador",
                3, 15000, "Moscow", null
        );
        ListingRequest request2 = new ListingRequest(
                "Unique Puppy 2", "Description", "dog", "Labrador",
                3, 15000, "Moscow", null
        );
        ListingRequest request3 = new ListingRequest(
                "Unique Puppy 3", "Description", "dog", "Labrador",
                3, 15000, "Moscow", null
        );

        ListingResponse listing1 = listingService.createListing(request1, 1L);
        ListingResponse listing2 = listingService.createListing(request2, 1L);
        ListingResponse listing3 = listingService.createListing(request3, 1L);


        setStatus(listing1.id(), ListingStatus.PUBLISHED);
        setStatus(listing2.id(), ListingStatus.PUBLISHED);
        // listing3 stays DRAFT

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<ListingResponse> page = listingService.getPublishedListings(pageable);

        // then
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allMatch(r -> r.status() == ListingStatus.PUBLISHED);
        assertThat(page.getContent().stream().map(ListingResponse::id))
                .containsExactlyInAnyOrder(listing1.id(), listing2.id())
                .doesNotContain(listing3.id());
    }

    @Test
    void getPublishedListings_WithPagination_ShouldReturnCorrectPage() throws InterruptedException {
        // given - create 5 published listings
        List<Long> createdIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ListingRequest request = new ListingRequest(
                    "Puppy " + i + " " + System.currentTimeMillis(),
                    "Description", "dog", "Labrador",
                    3, 15000 + i, "Moscow", null
            );
            ListingResponse created = listingService.createListing(request, 1L);
            setStatus(created.id(), ListingStatus.PUBLISHED);
            createdIds.add(created.id());

            Thread.sleep(1);
        }

        Pageable firstPage = PageRequest.of(0, 2);
        Pageable secondPage = PageRequest.of(1, 2);

        // when
        Page<ListingResponse> page1 = listingService.getPublishedListings(firstPage);
        Page<ListingResponse> page2 = listingService.getPublishedListings(secondPage);

        // then
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page2.getContent()).hasSize(2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);

        // verify different content on different pages (no order assumption)
        List<Long> page1Ids = page1.getContent().stream().map(ListingResponse::id).toList();
        List<Long> page2Ids = page2.getContent().stream().map(ListingResponse::id).toList();

        assertThat(page1Ids).doesNotContainAnyElementsOf(page2Ids);

        // verify all created listings are in either page1 or page2 (first two pages)
        List<Long> allFromFirstTwoPages = new ArrayList<>();
        allFromFirstTwoPages.addAll(page1Ids);
        allFromFirstTwoPages.addAll(page2Ids);
        assertThat(allFromFirstTwoPages).hasSize(4);
        assertThat(createdIds).containsAll(allFromFirstTwoPages);
    }

    @Test
    void getPublishedListings_WhenNoPublishedListings_ShouldReturnEmptyPage() {
        // given - create only draft listings
        ListingRequest request1 = new ListingRequest(
                "Draft Puppy 1", "Description", "dog", "Labrador",
                3, 15000, "Moscow", null
        );
        ListingRequest request2 = new ListingRequest(
                "Draft Puppy 2", "Description", "dog", "Labrador",
                3, 15000, "Moscow", null
        );

        listingService.createListing(request1, 1L);
        listingService.createListing(request2, 1L);

        Pageable pageable = PageRequest.of(0, 10);

        // when
        Page<ListingResponse> page = listingService.getPublishedListings(pageable);

        // then
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ==================== STATUS TRANSITION TESTS ====================

    @Test
    void updateListing_WhenModerationStatus_ShouldThrowInvalidStatusException() {
        // given - create listing as seller 1
        ListingResponse created = listingService.createListing(validRequest, 1L);

        // change status to MODERATION
        setStatus(created.id(), ListingStatus.MODERATION);

        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                "Updated Title", null, null, null, null, null, null, null
        );

        // when/then
        assertThatThrownBy(() -> listingService.updateListing(created.id(), updateRequest, 1L))
                .isInstanceOf(InvalidListingStatusException.class)
                .hasMessageContaining("Cannot edit listing in status: MODERATION");
    }

    @Test
    void updateListing_WhenRejectedStatus_ShouldUpdateSuccessfully() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        setStatus(created.id(), ListingStatus.REJECTED);

        ListingUpdateRequest updateRequest = new ListingUpdateRequest(
                "Updated Title", null, null, null, null, null, null, null
        );

        // when
        ListingResponse updated = listingService.updateListing(created.id(), updateRequest, 1L);

        // then
        assertThat(updated.title()).isEqualTo("Updated Title");
        assertThat(updated.status()).isEqualTo(ListingStatus.REJECTED); // unchanged
    }

    @Test
    void getListing_WhenModerationAndNotOwner_ShouldThrowAccessDenied() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        setStatus(created.id(), ListingStatus.MODERATION);

        // when/then
        assertThatThrownBy(() -> listingService.getListing(created.id(), 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getListing_WhenArchivedAndOwner_ShouldReturnListing() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        setStatus(created.id(), ListingStatus.ARCHIVED);

        // when
        ListingResponse response = listingService.getListing(created.id(), 1L);

        // then
        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.status()).isEqualTo(ListingStatus.ARCHIVED);
    }

// ==================== EDGE CASES ====================

    @Test
    void createListing_WithNullAgeAndPrice_ShouldSucceed() {
        // given
        ListingRequest requestWithoutAgeAndPrice = new ListingRequest(
                "Free Puppy", "Need good home", "dog", "Mixed",
                null, null, "Moscow", "passport-1"
        );

        // when
        ListingResponse response = listingService.createListing(requestWithoutAgeAndPrice, 1L);

        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.age()).isNull();
        assertThat(response.price()).isNull();
    }

    @Test
    void createListing_WithVeryLongDescription_ShouldSucceed() {
        // given
        String longDescription = "a".repeat(2000);
        ListingRequest requestWithLongDesc = new ListingRequest(
                "Puppy", longDescription, "dog", "Labrador",
                3, 15000, "Moscow", "passport-1"
        );

        // when
        ListingResponse response = listingService.createListing(requestWithLongDesc, 1L);

        // then
        assertThat(response.id()).isNotNull();
        assertThat(response.description()).isEqualTo(longDescription);
    }

    @Test
    void updateListing_WithEmptyUpdate_ShouldNotChangeAnything() {
        // given
        ListingResponse created = listingService.createListing(validRequest, 1L);
        ListingUpdateRequest emptyUpdate = new ListingUpdateRequest(
                null, null, null, null, null, null, null, null
        );

        // when
        ListingResponse updated = listingService.updateListing(created.id(), emptyUpdate, 1L);

        // then
        assertThat(updated.title()).isEqualTo(created.title());
        assertThat(updated.price()).isEqualTo(created.price());
        assertThat(updated.description()).isEqualTo(created.description());
    }

    // ==================== HELPER METHODS ====================

    private void setStatus(Long listingId, ListingStatus status) {
        Listing listing = listingRepository.findById(listingId).orElseThrow();
        listing.setStatus(status);
        listingRepository.save(listing);
    }
}
