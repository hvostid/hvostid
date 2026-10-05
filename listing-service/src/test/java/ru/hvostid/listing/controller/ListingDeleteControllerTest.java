package ru.hvostid.listing.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.listing.ListingIntegrationTest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;
import ru.hvostid.listing.service.ListingService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingDeleteControllerTest extends ListingIntegrationTest {

    private static final String LISTINGS_URL = "/api/v1/listings";
    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_USER_ID = 200L;
    private static final Long ADMIN_ID = 300L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ListingService listingService;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingFlagRepository flagRepository;

    @Autowired
    private ListingStatusHistoryRepository historyRepository;

    private Long draftListingId;
    private Long publishedListingId;
    private Long moderationListingId;

    @BeforeEach
    void setUp() {
        ListingRequest draftRequest =
                new ListingRequest("Draft Listing", "Description", "dog", "Breed", 6, 10000, "Moscow", "101");
        ListingResponse draftResponse = listingService.createListing(draftRequest, OWNER_ID);
        draftListingId = draftResponse.id();

        ListingRequest pubRequest =
                new ListingRequest("Published Listing", "Description", "dog", "Breed", 6, 10000, "Moscow", "102");
        ListingResponse pubResponse = listingService.createListing(pubRequest, OWNER_ID);
        publishedListingId = pubResponse.id();
        Listing pubListing = listingRepository.findById(publishedListingId).orElseThrow();
        pubListing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(pubListing);

        ListingRequest modRequest =
                new ListingRequest("Moderation Listing", "Description", "dog", "Breed", 6, 10000, "Moscow", "103");
        ListingResponse modResponse = listingService.createListing(modRequest, OWNER_ID);
        moderationListingId = modResponse.id();
        Listing modListing = listingRepository.findById(moderationListingId).orElseThrow();
        modListing.setStatus(ListingStatus.MODERATION);
        listingRepository.save(modListing);
    }

    @Test
    @DisplayName("Owner can delete DRAFT listing -> 204")
    void ownerCanDeleteDraftListing() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", draftListingId)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNoContent());

        assertThat(listingRepository.findById(draftListingId)).isEmpty();
    }

    @Test
    @DisplayName("Owner can delete PUBLISHED listing -> 204, history and flags cleaned")
    void ownerCanDeletePublishedListing() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", publishedListingId)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNoContent());

        assertThat(listingRepository.findById(publishedListingId)).isEmpty();
        assertThat(historyRepository.countByListingId(publishedListingId)).isZero();
        assertThat(flagRepository.countByListingId(publishedListingId)).isZero();
    }

    @Test
    @DisplayName("Non-owner cannot delete -> 403")
    void nonOwnerCannotDelete() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", draftListingId)
                        .header(USER_ID, OTHER_USER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isForbidden());

        assertThat(listingRepository.findById(draftListingId)).isPresent();
    }

    @Test
    @DisplayName("After deletion, GET returns 404")
    void afterDeleteGetReturnsNotFound() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", draftListingId)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(LISTINGS_URL + "/{id}", draftListingId)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Without auth -> 401")
    void withoutAuthCannotDelete() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", draftListingId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Delete listing in MODERATION -> 409")
    void cannotDeleteModerationListing() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", moderationListingId)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("under moderation")));

        assertThat(listingRepository.findById(moderationListingId)).isPresent();
    }

    @Test
    @DisplayName("Delete non-existent listing -> 404")
    void deleteNonExistentListing() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", 99999L)
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADMIN can delete any listing -> 204")
    void adminCanDeleteAnyListing() throws Exception {
        mockMvc.perform(delete(LISTINGS_URL + "/{id}", draftListingId)
                        .header(USER_ID, ADMIN_ID)
                        .header(USER_ROLES, UserRole.ADMIN.value()))
                .andExpect(status().isNoContent());

        assertThat(listingRepository.findById(draftListingId)).isEmpty();
    }
}
