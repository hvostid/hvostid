package ru.hvostid.listing.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;
import static ru.hvostid.common.http.SecurityHeaders.USER_ROLES;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingControllerTest extends AbstractPostgresContainerTest {

    private static final String LISTINGS_URL = "/api/v1/listings";
    private final Long testSellerId = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ListingRepository listingRepository;

    @Nested
    @DisplayName("POST /api/v1/listings")
    class CreateListingTests {

        @Test
        @DisplayName("SELLER can create listing - returns 201")
        void createListing_withSellerRole_returns201() throws Exception {
            ListingRequest request = new ListingRequest(
                    "Cute Puppy", "Friendly dog", "dog", "Labrador", 3, 15000, "Moscow", "passport-1");

            mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.title", is("Cute Puppy")))
                    .andExpect(jsonPath("$.status", is("DRAFT")));
        }

        @Test
        @DisplayName("BUYER cannot create listing - returns 403")
        void createListing_withBuyerRole_returns403() throws Exception {
            ListingRequest request = new ListingRequest(
                    "Cute Puppy", "Friendly dog", "dog", "Labrador", 3, 15000, "Moscow", "passport-1");

            mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, 200L)
                            .header(USER_ROLES, UserRole.BUYER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("create listing invalid title -> 400")
        void create_invalidTitle_returns400() throws Exception {
            ListingRequest request = new ListingRequest("ab", "Desc", "dog", "Labrador", 3, 10000, "Moscow", "p-1");

            mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.title").exists());
        }

        @Test
        void create_invalidUserId_returns401() throws Exception {
            ListingRequest request = new ListingRequest("Test", "Desc", "dog", "Labrador", 3, 10000, "Moscow", "p-1");

            mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, -1)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/listings")
    class GetListingsTests {

        @BeforeEach
        void setUp() {
            // 2 published
            for (int i = 0; i < 2; i++) {
                Listing l =
                        new Listing(testSellerId, "Pub " + i, "Desc", "dog", "Labrador", 3, 10000, "Moscow", "p-" + i);
                l.setStatus(ListingStatus.PUBLISHED);
                listingRepository.save(l);
            }

            // 1 draft
            Listing draft = new Listing(testSellerId, "Draft", "Desc", "dog", "Labrador", 3, 10000, "Moscow", "p-x");
            draft.setStatus(ListingStatus.DRAFT);
            listingRepository.save(draft);
        }

        @Test
        @DisplayName("should return only published listings")
        void getListings_onlyPublished() throws Exception {
            mockMvc.perform(get(LISTINGS_URL + "?page=0&size=10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[*].status", everyItem(is("PUBLISHED"))));
        }

        @Test
        @DisplayName("should support pagination")
        void getListings_pagination() throws Exception {
            mockMvc.perform(get(LISTINGS_URL + "?page=0&size=1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/listings/{id}")
    class GetListingTests {

        private Long createdListingId;

        @BeforeEach
        void setUp() {
            // Create a listing in the database before each test.
            Listing listing = new Listing(
                    testSellerId, "Test Puppy", "Description", "dog", "Husky", 6, 20000, "Moscow", "passport-1");
            listing.setStatus(ListingStatus.PUBLISHED);
            createdListingId = listingRepository.save(listing).getId();
        }

        @Test
        @DisplayName("published listing - accessible by any user")
        void getListing_published_returns200() throws Exception {
            mockMvc.perform(get(LISTINGS_URL + "/{id}", createdListingId).header(USER_ID, 999L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(createdListingId.intValue())))
                    .andExpect(jsonPath("$.status", is("PUBLISHED")));
        }

        @Test
        @DisplayName("draft listing - accessible only by owner")
        void getListing_draftAndOwner_returns200() throws Exception {
            // Draft listing.
            Listing draftListing = new Listing(
                    testSellerId, "Draft Puppy", "Description", "dog", "Husky", 6, 20000, "Moscow", "passport-1");
            draftListing.setStatus(ListingStatus.DRAFT);
            Long draftId = listingRepository.save(draftListing).getId();

            mockMvc.perform(get(LISTINGS_URL + "/{id}", draftId).header(USER_ID, testSellerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DRAFT")));
        }

        @Test
        @DisplayName("draft listing - not accessible by other user")
        void getListing_draftAndNotOwner_returns403() throws Exception {
            // Draft listing.
            Listing draftListing = new Listing(
                    testSellerId, "Draft Puppy", "Description", "dog", "Husky", 6, 20000, "Moscow", "passport-1");
            draftListing.setStatus(ListingStatus.DRAFT);
            Long draftId = listingRepository.save(draftListing).getId();

            mockMvc.perform(get(LISTINGS_URL + "/{id}", draftId).header(USER_ID, 999L))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/listings/{id} - Edge Cases")
    class UpdateListingEdgeCasesTests {

        private Long listingId;

        @BeforeEach
        void setUp() {
            ListingRequest request = new ListingRequest(
                    "Test Puppy", "Description", "dog", "Labrador", 3, 15000, "Moscow", "passport-1");

            try {
                String responseJson = mockMvc.perform(post(LISTINGS_URL)
                                .header(USER_ID, testSellerId)
                                .header(USER_ROLES, UserRole.SELLER.value())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

                listingId = objectMapper.readTree(responseJson).get("id").asLong();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("update non-existent listing - returns 404")
        void updateListing_notFound_returns404() throws Exception {
            ListingUpdateRequest updateRequest =
                    new ListingUpdateRequest("Updated", null, null, null, null, null, null, null);

            mockMvc.perform(put(LISTINGS_URL + "/{id}", 99999L)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("update listing with invalid data - returns 400")
        void updateListing_invalidData_returns400() throws Exception {
            // title too short
            ListingUpdateRequest invalidUpdate =
                    new ListingUpdateRequest("ab", null, null, null, null, null, null, null);

            mockMvc.perform(put(LISTINGS_URL + "/{id}", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUpdate)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.title").value("Title must be between 3 and 255 characters"));
        }

        @Test
        @DisplayName("update listing with negative price - returns 400")
        void updateListing_negativePrice_returns400() throws Exception {
            ListingUpdateRequest invalidUpdate =
                    new ListingUpdateRequest(null, null, null, null, null, -100, null, null);

            mockMvc.perform(put(LISTINGS_URL + "/{id}", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidUpdate)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.price").value("Price must be positive"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/listings/{id}")
    class UpdateListingTests {

        private Long listingId;

        @BeforeEach
        void setUp() throws Exception {
            ListingRequest request = new ListingRequest("Test", "Desc", "dog", "Labrador", 3, 10000, "Moscow", "p-1");

            String response = mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            listingId = objectMapper.readTree(response).get("id").asLong();
        }

        @Test
        void update_success() throws Exception {
            ListingUpdateRequest req = new ListingUpdateRequest("Updated", null, null, null, null, null, null, null);

            mockMvc.perform(put(LISTINGS_URL + "/{id}", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated"));
        }

        @Test
        void update_notOwner_returns403() throws Exception {
            ListingUpdateRequest req = new ListingUpdateRequest("Hack", null, null, null, null, null, null, null);

            mockMvc.perform(put(LISTINGS_URL + "/{id}", listingId)
                            .header(USER_ID, 999L)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/listings/{id}/status")
    class UpdateStatusTests {

        private Long listingId;

        @BeforeEach
        void setUp() throws Exception {
            ListingRequest request = new ListingRequest(
                    "Status Test Puppy", "Description", "dog", "Labrador", 3, 15000, "Moscow", "passport-1");

            String response = mockMvc.perform(post(LISTINGS_URL)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            listingId = objectMapper.readTree(response).get("id").asLong();
        }

        @Test
        @DisplayName("owner can send DRAFT to MODERATION - returns 200")
        void ownerCanSendToModeration() throws Exception {
            String requestBody = """
                {"status": "MODERATION"}
                """;

            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(listingId.intValue())))
                    .andExpect(jsonPath("$.status", is("MODERATION")));
        }

        @Test
        @DisplayName("moderator can publish MODERATION -> PUBLISHED - returns 200")
        void moderatorCanPublish() throws Exception {
            // First, seller sends to MODERATION
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            // Then moderator publishes
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, 500L)
                            .header(USER_ROLES, UserRole.MODERATOR.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PUBLISHED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("PUBLISHED")));
        }

        @Test
        @DisplayName("non-moderator cannot publish - returns 403")
        void nonModeratorCannotPublish() throws Exception {
            // First, seller sends to MODERATION
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            // Owner (non-moderator) tries to publish - should fail
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PUBLISHED\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("invalid transition returns 422")
        void invalidTransitionReturns422() throws Exception {
            // DRAFT -> PUBLISHED directly is invalid
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PUBLISHED\"}"))
                    .andExpect(status().isUnprocessableContent()); // 422
        }

        @Test
        @DisplayName("moderator can return to DRAFT with comment - returns 200")
        void moderatorCanReturnToDraftWithComment() throws Exception {
            // First, seller sends to MODERATION
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            // Moderator returns to DRAFT with comment
            String requestBody = """
                {"status": "DRAFT", "comment": "Please add more photos"}
                """;

            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, 500L)
                            .header(USER_ROLES, UserRole.MODERATOR.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("DRAFT")));
        }

        @Test
        @DisplayName("moderator can reject listing - returns 200")
        void moderatorCanReject() throws Exception {
            // First, seller sends to MODERATION
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            // Moderator rejects
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, 500L)
                            .header(USER_ROLES, UserRole.MODERATOR.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"REJECTED\", \"comment\": \"Poor quality\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("REJECTED")));
        }

        @Test
        @DisplayName("owner can archive published listing - returns 200")
        void ownerCanArchive() throws Exception {
            // First, seller sends to MODERATION, moderator publishes
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, 500L)
                            .header(USER_ROLES, UserRole.MODERATOR.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PUBLISHED\"}"))
                    .andExpect(status().isOk());

            // Owner archives
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"ARCHIVED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("ARCHIVED")));
        }

        @Test
        @DisplayName("owner can mark as sold - returns 200")
        void ownerCanMarkAsSold() throws Exception {
            // First, seller sends to MODERATION, moderator publishes
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"MODERATION\"}"))
                    .andExpect(status().isOk());

            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, 500L)
                            .header(USER_ROLES, UserRole.MODERATOR.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"PUBLISHED\"}"))
                    .andExpect(status().isOk());

            // Owner marks as sold
            mockMvc.perform(patch(LISTINGS_URL + "/{id}/status", listingId)
                            .header(USER_ID, testSellerId)
                            .header(USER_ROLES, UserRole.SELLER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\": \"SOLD\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("SOLD")));
        }
    }
}
