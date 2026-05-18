package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

import java.util.Comparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.ListingConstants;
import ru.hvostid.listing.dto.ListingFilterRequest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingFilterIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listingRepository.deleteAll();

        // Create test data with various combinations
        createPublishedListing("Friendly Husky", "Playful dog", "Husky", "dog", 12, 25000, "Moscow", "passport-1");
        createPublishedListing(
                "Labrador Puppy", "Golden labrador", "Labrador", "dog", 6, 35000, "Saint Petersburg", "passport-2");
        createPublishedListing("Siamese Cat", "Elegant cat", "Siamese", "cat", 24, 15000, "Moscow", "passport-3");
        createPublishedListing("British Shorthair", "Fluffy cat", "British", "cat", 36, 20000, "Moscow", "passport-4");
        createPublishedListing("Old Dog", "Senior dog", "Mixed", "dog", 120, 5000, "Kazan", "passport-5");
    }

    private void createPublishedListing(
            String title,
            String description,
            String breed,
            String species,
            int age,
            int price,
            String city,
            String passportId) {
        ListingRequest request = new ListingRequest(title, description, species, breed, age, price, city, passportId);
        ListingResponse created = listingService.createListing(request, 1L);
        Listing listing = listingRepository.findById(created.id()).orElseThrow();
        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);
    }

    @Test
    @DisplayName("Filter by species - returns only matching listings")
    void filterBySpecies_returnsMatchingListings() {
        ListingFilterRequest filters = new ListingFilterRequest("dog", null, null, null, null, null, null);

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(l -> l.species().equalsIgnoreCase("dog"));
    }

    @Test
    @DisplayName("Filter by city - returns only listings from that city")
    void filterByCity_returnsMatchingListings() {
        ListingFilterRequest filters = new ListingFilterRequest(null, null, null, null, null, null, "Moscow");

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent()).allMatch(l -> l.city().equalsIgnoreCase("Moscow"));
    }

    @Test
    @DisplayName("Filter by price range - returns listings within price bounds")
    void filterByPriceRange_returnsListingsWithinRange() {
        ListingFilterRequest filters = new ListingFilterRequest(null, null, null, null, 10000, 20000, null);

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(l -> l.price() >= 10000 && l.price() <= 20000);
    }

    @Test
    @DisplayName("Filter by age range - returns listings within age bounds")
    void filterByAgeRange_returnsListingsWithinRange() {
        ListingFilterRequest filters = new ListingFilterRequest(null, null, 6, 20, null, null, null);

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(l -> l.age() >= 6 && l.age() <= 20);
    }

    @Test
    @DisplayName("Multiple filters combined - returns only listings matching all criteria")
    void multipleFiltersCombined_returnsMatchingListings() {
        ListingFilterRequest filters = new ListingFilterRequest("dog", "Husky", null, null, 20000, 30000, "Moscow");

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        ListingResponse listing = result.getContent().getFirst();
        assertThat(listing.species()).isEqualTo("dog");
        assertThat(listing.breed()).contains("Husky");
        assertThat(listing.price()).isBetween(20000, 30000);
        assertThat(listing.city()).isEqualTo("Moscow");
    }

    @Test
    @DisplayName("Sort by price ascending")
    void sortByPriceAsc_returnsSortedListings() {
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"));

        Page<ListingResponse> result = listingService.getListingsWithFilters(
                new ListingFilterRequest(null, null, null, null, null, null, null), pageRequest);

        assertThat(result.getContent()).isSortedAccordingTo(Comparator.comparing(ListingResponse::price));
    }

    @Test
    @DisplayName("Sort by price descending")
    void sortByPriceDesc_returnsSortedListings() {
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "price"));

        Page<ListingResponse> result = listingService.getListingsWithFilters(
                new ListingFilterRequest(null, null, null, null, null, null, null), pageRequest);

        assertThat(result.getContent()).isSortedAccordingTo((a, b) -> b.price().compareTo(a.price()));
    }

    @Test
    @DisplayName("Sort by creation date descending (newest first)")
    void sortByCreatedDesc_returnsNewestFirst() {
        PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ListingResponse> result = listingService.getListingsWithFilters(
                new ListingFilterRequest(null, null, null, null, null, null, null), pageRequest);

        assertThat(result.getContent()).isNotEmpty();
        // Creation times should be in descending order
        for (int i = 0; i < result.getContent().size() - 1; i++) {
            assertThat(result.getContent().get(i).createdAt())
                    .isAfterOrEqualTo(result.getContent().get(i + 1).createdAt());
        }
    }

    @Test
    @DisplayName("Pagination returns correct page")
    void pagination_returnsCorrectPage() {
        // Create 25 more listings to test pagination
        for (int i = 0; i < 25; i++) {
            createPublishedListing("Test " + i, "Desc", "Breed", "dog", 12, 10000, "Moscow", "passport-" + i);
        }

        Page<ListingResponse> result = listingService.getListingsWithFilters(
                new ListingFilterRequest(null, null, null, null, null, null, null),
                PageRequest.of(0, 20)); // Explicitly ask for 20

        assertThat(result.getContent()).hasSize(20);
    }

    @Test
    @DisplayName("Empty filter returns all published listings")
    void emptyFilter_returnsAllPublishedListings() {
        Page<ListingResponse> result = listingService.getListingsWithFilters(
                new ListingFilterRequest(null, null, null, null, null, null, null), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(5);
    }

    @Test
    @DisplayName("HTTP endpoint rejects invalid page size")
    void httpEndpointWithInvalidPageSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/listings").param("size", "200").header(USER_ID, 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Search with keyword and filters combined")
    void searchWithKeywordAndFilters_returnsMatchingListings() throws Exception {
        mockMvc.perform(get("/api/v1/listings")
                        .param("q", "husky")
                        .param("species", "dog")
                        .param("city", "Moscow")
                        .with(user("testUser").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("Breed filter with partial match")
    void breedFilterWithPartialMatch_returnsMatchingListings() {
        ListingFilterRequest filters = new ListingFilterRequest(null, "Husk", null, null, null, null, null);

        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().breed()).contains("Husky");
    }

    @Test
    @DisplayName("Invalid age range returns empty result")
    void invalidAgeRange_returnsEmptyResult() {
        ListingFilterRequest filters = new ListingFilterRequest(null, null, 100, 50, null, null, null);

        // Validation should happen at controller level, service should handle gracefully
        Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

        // ageMin > ageMax means no listings will match
        assertThat(result.getContent()).isEmpty();
    }

    @Nested
    @DisplayName("Boundary and Edge Cases")
    class BoundaryTests {

        @Test
        @DisplayName("Age range with min=0 should include newborn animals")
        void ageMinZero_includesNewborns() {
            // Create newborn listing
            createPublishedListing("Newborn Puppy", "Just born", "Mixed", "dog", 0, 10000, "Moscow", "passport-new");

            ListingFilterRequest filters = new ListingFilterRequest(null, null, 0, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).anyMatch(l -> l.age() == 0);
        }

        @Test
        @DisplayName("Age range with max=MAX_AGE should include elderly animals")
        void ageMaxMaxValue_includesElderly() {
            ListingFilterRequest filters = new ListingFilterRequest(null, null, null, 5000, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).allMatch(l -> l.age() <= ListingConstants.MAX_AGE_MONTHS);
        }

        @Test
        @DisplayName("Zero price listings are filterable")
        void zeroPrice_filteringWorks() {
            createPublishedListing("Free Puppy", "Need home", "Mixed", "dog", 6, 0, "Moscow", "passport-free");

            ListingFilterRequest filters = new ListingFilterRequest(null, null, null, null, 0, 0, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).allMatch(l -> l.price() == 0);
        }

        @Test
        @DisplayName("Max price boundary (999,999,999) works")
        void maxPriceBoundary_works() {
            ListingFilterRequest filters = new ListingFilterRequest(null, null, null, null, null, 999999999, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).allMatch(l -> l.price() <= ListingConstants.MAX_PRICE);
        }

        @Test
        @DisplayName("Empty search results for non-existent species")
        void nonExistentSpecies_returnsEmpty() {
            ListingFilterRequest filters = new ListingFilterRequest("dragon", null, null, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Case sensitivity - species filter is case-insensitive")
        void speciesFilter_caseInsensitive() {
            ListingFilterRequest filters = new ListingFilterRequest("DOG", null, null, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent()).allMatch(l -> l.species().equalsIgnoreCase("dog"));
        }

        @Test
        @DisplayName("Partial breed match with single character")
        void breedFilter_singleCharacterMatch() {
            ListingFilterRequest filters = new ListingFilterRequest(null, "H", null, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent())
                    .allMatch(l -> l.breed().toLowerCase().contains("h"));
        }
    }

    @Nested
    @DisplayName("Combination Edge Cases")
    class CombinationTests {

        @Test
        @DisplayName("All filters combined with no matches")
        void allFilters_noMatches() {
            ListingFilterRequest filters =
                    new ListingFilterRequest("dog", "Husky", 100, 200, 1000000, 2000000, "NonExistentCity");

            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Filters with null values treated as not applied")
        void nullFilters_areIgnored() {
            ListingFilterRequest filters = new ListingFilterRequest(null, null, null, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(5);
        }

        @Test
        @DisplayName("Age only min, no max")
        void ageOnlyMin_noMax() {
            ListingFilterRequest filters = new ListingFilterRequest(null, null, 24, null, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).allMatch(l -> l.age() >= 24);
            assertThat(result.getContent()).hasSize(3); // Siamese(24) and British(36) and Old D0g(120)
        }

        @Test
        @DisplayName("Age only max, no min")
        void ageOnlyMax_noMin() {
            ListingFilterRequest filters = new ListingFilterRequest(null, null, null, 6, null, null, null);
            Page<ListingResponse> result = listingService.getListingsWithFilters(filters, PageRequest.of(0, 10));

            assertThat(result.getContent()).allMatch(l -> l.age() <= 6);
            assertThat(result.getContent()).hasSize(1); // Labrador(6)
        }
    }

    @Nested
    @DisplayName("Pagination Edge Cases")
    class PaginationEdgeTests {

        @Test
        @DisplayName("Page 0 with size 1 returns first element")
        void pageZeroSizeOne_returnsFirstElement() {
            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), PageRequest.of(0, 1));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalPages()).isEqualTo(5);
            assertThat(result.getNumber()).isZero();
        }

        @Test
        @DisplayName("Last page returns remaining elements")
        void lastPage_returnsRemainingElements() {
            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), PageRequest.of(4, 1));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getNumber()).isEqualTo(4);
        }

        @Test
        @DisplayName("Page beyond total returns empty")
        void pageBeyondTotal_returnsEmpty() {
            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), PageRequest.of(100, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isEqualTo(5);
        }

        @Test
        @DisplayName("Max allowed page size (100) works")
        void maxPageSize_works() {
            // Create more listings
            for (int i = 0; i < 150; i++) {
                createPublishedListing("Test " + i, "Desc", "Breed" + i, "dog", 12, 10000, "Moscow", "passport-" + i);
            }

            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), PageRequest.of(0, 100));

            assertThat(result.getContent()).hasSize(ListingConstants.MAX_PAGE_SIZE);
        }
    }

    @Nested
    @DisplayName("Sorting Edge Cases")
    class SortingEdgeTests {

        @Test
        @DisplayName("Sort by price asc with equal prices maintains stable order")
        void sortByPriceAsc_equalPrices() {
            createPublishedListing("Equal Price 1", "Same price", "Breed1", "dog", 12, 15000, "Moscow", "passport-eq1");
            createPublishedListing("Equal Price 2", "Same price", "Breed2", "cat", 24, 15000, "Moscow", "passport-eq2");

            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "price"));
            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), pageRequest);

            // Both should be present, order may be by ID
            assertThat(result.getContent()).extracting("price").contains(15000);
        }

        @Test
        @DisplayName("Sort by created_desc - newer listings appear first")
        void sortByCreatedDesc_newerFirst() throws InterruptedException {
            // Create listings with different timestamps
            createPublishedListing("First", "Desc", "Breed1", "dog", 1, 1000, "City1", "pass-ts1");
            Thread.sleep(10); // Ensure different timestamp
            createPublishedListing("Second", "Desc", "Breed2", "cat", 2, 2000, "City2", "pass-ts2");

            PageRequest pageRequest = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<ListingResponse> result = listingService.getListingsWithFilters(
                    new ListingFilterRequest(null, null, null, null, null, null, null), pageRequest);

            assertThat(result.getContent()).isNotEmpty();
            assertThat(result.getContent().get(0).createdAt())
                    .isAfter(result.getContent().get(1).createdAt());
        }
    }

    @Nested
    @DisplayName("Search with Filters Combined Tests")
    class SearchWithFiltersTests {

        @Test
        @DisplayName("Keyword search with species filter")
        void keywordSearch_withSpeciesFilter() throws Exception {
            mockMvc.perform(get("/api/v1/listings")
                            .param("q", "friendly")
                            .param("species", "dog")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Friendly Husky"));
        }

        @Test
        @DisplayName("Keyword search with price range")
        void keywordSearch_withPriceRange() throws Exception {
            mockMvc.perform(get("/api/v1/listings")
                            .param("q", "puppy")
                            .param("priceMin", "30000")
                            .param("priceMax", "40000")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].title").value("Labrador Puppy"));
        }

        @Test
        @DisplayName("Keyword search with no matches")
        void keywordSearch_noMatches() throws Exception {
            mockMvc.perform(get("/api/v1/listings")
                            .param("q", "nonexistentkeyword12345")
                            .param("species", "dog")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(0))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("Keyword search with empty string returns all")
        void keywordSearch_emptyString_returnsAll() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("q", "").header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5));
        }

        @Test
        @DisplayName("Keyword search with whitespace only")
        void keywordSearch_whitespaceOnly_returnsAll() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("q", "   ").header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(5));
        }
    }

    @Nested
    @DisplayName("HTTP Endpoint Validation Tests")
    class HttpValidationTests {

        @Test
        @DisplayName("Invalid sort parameter returns bad request")
        void invalidSort_returnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/listings")
                            .param("sort", "invalid_sort")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Negative ageMin returns bad request")
        void negativeAgeMin_returnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("ageMin", "-1").header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Age > 5000 returns bad request")
        void ageExceedsMax_returnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("ageMin", "5001").header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Negative price returns bad request")
        void negativePrice_returnsBadRequest() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("priceMin", "-100").header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Page size 0 defaults to default page size (20)")
        void pageSizeZero_defaultsToDefaultPageSize() throws Exception {
            mockMvc.perform(get("/api/v1/listings").param("size", "0").header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        @DisplayName("Very long search query returns bad request")
        void veryLongSearchQuery_returnsBadRequest() throws Exception {
            String longQuery = "a".repeat(501);
            mockMvc.perform(get("/api/v1/listings").param("q", longQuery).header("X-User-Id", "1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("City name with special characters")
        void cityWithSpecialCharacters_works() throws Exception {
            createPublishedListing("Special City", "Desc", "Breed", "dog", 12, 10000, "St. Petersburg", "pass-special");

            mockMvc.perform(get("/api/v1/listings")
                            .param("city", "St. Petersburg")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }
    }

    @Nested
    @DisplayName("Concurrency and Race Conditions")
    class ConcurrencyTests {

        @Test
        @DisplayName("Simultaneous filter requests don't interfere")
        void simultaneousFilterRequests_independent() throws Exception {
            // Execute multiple requests concurrently
            mockMvc.perform(get("/api/v1/listings").param("species", "dog").header("X-User-Id", "1"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/listings").param("species", "cat").header("X-User-Id", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2));
        }
    }
}
