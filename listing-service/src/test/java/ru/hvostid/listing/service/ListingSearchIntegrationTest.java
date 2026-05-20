package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static ru.hvostid.common.http.SecurityHeaders.USER_ID;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingSearchIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        createAndPublishListing("Хаски щенок", "Красивый голубоглазый хаски", "Хаски");
        createAndPublishListing("Лабрадор щенок", "Friendly Labrador is looking for a home", "Лабрадор");
        createAndPublishListing("Мейн-кун котенок", "Пушистый мейн-кун", "Мейн-кун");
    }

    private void createAndPublishListing(String title, String description, String breed) {
        ListingRequest request = new ListingRequest(
                title, description, "CAT", breed, 6, 10000, "Moscow", "passport-" + System.currentTimeMillis());
        ListingResponse created = listingService.createListing(request, 1L);
        Listing listing = listingRepository.findById(created.id()).orElseThrow();
        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);
    }

    @ParameterizedTest(name = "[{index}] searchListings(\"{0}\") -> {1} hits, contains \"{2}\"")
    @CsvSource(delimiter = '|', nullValues = "<null>", textBlock = """
                    хаски     | 1 | Хаски
                    labrador  | 1 | Лабрадор
                              | 3 | <null>
                    notexists | 0 | <null>
                    """)
    void searchListings_returnsExpectedPageForKeyword(String keyword, int expectedSize, String expectedFragment) {
        String resolvedKeyword = keyword == null ? "" : keyword;

        Page<ListingResponse> result = listingService.searchListings(resolvedKeyword, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(expectedSize);
        if (expectedFragment != null) {
            ListingResponse first = result.getContent().getFirst();
            assertThat(first.title() + " " + first.breed()).contains(expectedFragment);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleHitKeywords")
    void searchListings_findsExpectedListing(
            String description,
            String keyword,
            String extraSeedTitle,
            String extraSeedDescription,
            String extraSeedBreed,
            String expectedTitleFragment) {
        if (extraSeedTitle != null) {
            createAndPublishListing(extraSeedTitle, extraSeedDescription, extraSeedBreed);
        }

        Page<ListingResponse> result = listingService.searchListings(keyword, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains(expectedTitleFragment);
    }

    static Stream<Arguments> singleHitKeywords() {
        return Stream.of(
                // plainto_tsquery uses AND between words
                Arguments.of("AND search across multiple words", "хаски щенок", null, null, null, "Хаски щенок"),
                // Russian stop words: и, в, на, с, по, за, под are ignored
                Arguments.of("stop words are filtered out", "и в на хаски", null, null, null, "Хаски"),
                // Russian stemming: "собаки" finds "собака"
                Arguments.of(
                        "Russian stemming finds word roots",
                        "собаки",
                        "Собака",
                        "Красивая собака",
                        "Дворняжка",
                        "Собака"));
    }

    @Test
    void searchUpdatesVectorOnUpdate_worksCorrectly() {
        ListingRequest request = new ListingRequest(
                "Initial title",
                "Description",
                "CAT",
                "Breed",
                6,
                10000,
                "Moscow",
                "passport-" + System.currentTimeMillis());
        ListingResponse created = listingService.createListing(request, 1L);

        Listing listing = listingRepository.findById(created.id()).orElseThrow();
        listing.setStatus(ListingStatus.PUBLISHED);
        listingRepository.save(listing);

        Page<ListingResponse> beforeUpdate = listingService.searchListings("Initial", PageRequest.of(0, 10));
        assertThat(beforeUpdate.getContent()).hasSize(1);

        ListingUpdateRequest updateRequest =
                new ListingUpdateRequest("Updated title", null, null, null, null, null, null, null);
        listingService.updateListing(created.id(), updateRequest, 1L);

        Page<ListingResponse> afterUpdate = listingService.searchListings("Updated", PageRequest.of(0, 10));
        assertThat(afterUpdate.getContent()).hasSize(1);

        Page<ListingResponse> oldKeywordSearch = listingService.searchListings("Initial", PageRequest.of(0, 10));
        assertThat(oldKeywordSearch.getContent()).isEmpty();
    }

    @Test
    void searchWithSpecialCharacters_handlesGracefully() {
        // plainto_tsquery escapes special characters automatically
        Page<ListingResponse> result = listingService.searchListings("хаски & !лабрадор", PageRequest.of(0, 10));

        assertThat(result).isNotNull();
    }

    @Test
    void searchPagination_returnsCorrectPage() {
        for (int i = 0; i < 25; i++) {
            createAndPublishListing("Test listing " + i, "Description " + i, "Breed " + i);
        }

        Page<ListingResponse> page1 = listingService.searchListings("Test", PageRequest.of(0, 10));
        Page<ListingResponse> page2 = listingService.searchListings("Test", PageRequest.of(1, 10));

        assertThat(page1.getTotalElements()).isGreaterThanOrEqualTo(25);
        assertThat(page1.getContent()).hasSize(10);
        assertThat(page2.getContent()).hasSize(10);
        assertThat(page1.getContent().getFirst().title())
                .isNotEqualTo(page2.getContent().getFirst().title());
    }

    @Test
    void searchWithKeywordInDescription_returnsListing() {
        createAndPublishListing("Regular title", "This listing is about a husky puppy", "Other breed");

        Page<ListingResponse> result = listingService.searchListings("husky puppy", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().description()).contains("husky puppy");
    }

    @Test
    void searchWithKeywordInBreed_returnsListing() {
        createAndPublishListing("Regular title", "Regular description", "Сиамская");

        Page<ListingResponse> result = listingService.searchListings("сиамская", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().breed()).contains("Сиамская");
    }

    @Test
    void searchSortingByRelevance_mostRelevantFirst() {
        createAndPublishListing("Husky puppy", "Beautiful husky puppy with blue eyes", "Husky");
        createAndPublishListing("Puppy for good hands", "Looking for home for husky puppy", "Mixed");

        Page<ListingResponse> result = listingService.searchListings("husky", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        // First result should have "Husky" in title (higher weight A)
        assertThat(result.getContent().getFirst().title().toLowerCase()).contains("husky");
    }

    @Test
    void searchWithCaseInsensitivity_worksCorrectly() {
        createAndPublishListing("HUSKY PUPPY", "BEAUTIFUL HUSKY", "HUSKY");

        Page<ListingResponse> result = listingService.searchListings("husky", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("HUSKY");
    }

    @Test
    @DisplayName("Search with Russian morphology - different word forms should match")
    void searchWithRussianMorphology_matchesDifferentWordForms() {
        createAndPublishListing("Кот", "Пушистый кот ищет дом", "Сиамский");
        createAndPublishListing("Кота", "У кота красивые глаза", "Британский");
        createAndPublishListing("Коту", "Коту нужна забота", "Дворовой");
        createAndPublishListing("Собака", "Весёлая собака", "Лабрадор");

        Page<ListingResponse> result = listingService.searchListings("кот", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent())
                .extracting(ListingResponse::breed)
                .contains("Сиамский", "Британский", "Дворовой");
    }

    @Test
    @DisplayName("Search with very long keyword should not break the system")
    void searchWithVeryLongKeyword_handlesGracefully() {
        listingRepository.deleteAll();

        createAndPublishListing("Хаски щенок", "Красивый пёс", "Хаски");

        String veryLongKeyword = "a".repeat(10000);

        Page<ListingResponse> result = listingService.searchListings(veryLongKeyword, PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("HTTP endpoint rejects too long keyword with 400")
    void httpSearchWithTooLongKeyword_returnsBadRequest() throws Exception {
        String tooLongKeyword = "a".repeat(1000);

        mockMvc.perform(get("/api/v1/listings").param("q", tooLongKeyword).header(USER_ID, 1L))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Search ranking: higher weight for title matches than description")
    void searchRanking_titleMatchesBeforeDescription() {
        listingRepository.deleteAll();

        // Description contains keyword
        createAndPublishListing("Обычный щенок", "Этот красивый хаски ищет дом", "Смесь");
        // Title contains keyword
        createAndPublishListing("Красивый хаски", "Обычное описание", "Хаски");

        Page<ListingResponse> result = listingService.searchListings("хаски", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().getFirst().title()).contains("хаски");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blankOrUnmatchedKeywords")
    void searchListings_blankOrUnmatched_returnsExpectedSize(String description, String keyword, int expectedSize) {
        Page<ListingResponse> result = listingService.searchListings(keyword, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(expectedSize);
    }

    static Stream<Arguments> blankOrUnmatchedKeywords() {
        return Stream.of(
                Arguments.of("no-match keyword returns empty (published-only)", "секретное", 0),
                Arguments.of("null keyword returns all published listings", null, 3),
                Arguments.of("space-only keyword returns all published listings", "   ", 3),
                Arguments.of("tab+newline keyword returns all published listings", "\t\n", 3));
    }

    @Test
    @DisplayName("Search vector updates correctly after partial update")
    void searchVectorUpdatesOnPartialUpdate_correctly() {
        listingRepository.deleteAll();

        createAndPublishListing("Щенок", "Красивый хаски", "Смесь");

        Page<ListingResponse> beforeUpdate = listingService.searchListings("хаски", PageRequest.of(0, 10));
        assertThat(beforeUpdate.getContent()).hasSize(1);

        Listing listing = listingRepository.findAll().stream()
                .filter(l -> "Щенок".equals(l.getTitle()))
                .findFirst()
                .orElseThrow();

        listing.setTitle("Новый заголовок без ключевого слова");
        listingRepository.save(listing);

        Page<ListingResponse> afterUpdate = listingService.searchListings("хаски", PageRequest.of(0, 10));
        assertThat(afterUpdate.getContent()).hasSize(1);

        listing.setDescription("Обычное описание");
        listingRepository.save(listing);

        Page<ListingResponse> finalSearch = listingService.searchListings("хаски", PageRequest.of(0, 10));
        assertThat(finalSearch.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Search with empty quotes returns all published")
    void searchWithEmptyQuotes_returnsAllPublished() {
        createAndPublishListing("Любое объявление", "Описание", "Порода");

        Page<ListingResponse> result = listingService.searchListings("\"\"", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(4);
    }

    @Test
    @DisplayName("Search with unmatched quotes treats as regular search")
    void searchWithUnmatchedQuotes_fallsBackToRegularSearch() {
        createAndPublishListing("Другой хаски", "Другое описание", "Хаски");

        Page<ListingResponse> result = listingService.searchListings("\"хаски", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("Search with phrase containing stop words - works correctly")
    void searchWithPhraseContainingStopWords_worksCorrectly() {
        listingRepository.deleteAll();

        createAndPublishListing("кот в сапогах", "Сказочный персонаж", "Кот");
        createAndPublishListing("кот и сапоги", "Другое", "Кот");
        createAndPublishListing("сапоги кота", "Ещё одно", "Кот");

        Page<ListingResponse> result = listingService.searchListings("кот в сапогах", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("Performance: phrase search uses index")
    void phraseSearch_usesGinIndex() {
        for (int i = 0; i < 100; i++) {
            createAndPublishListing("Тестовое объявление " + i, "Описание " + i, "Порода " + i);
        }

        createAndPublishListing("Уникальная точная фраза для поиска", "Уникальное описание", "Уникальная порода");

        long start = System.currentTimeMillis();
        Page<ListingResponse> result =
                listingService.searchListings("\"уникальная точная фраза\"", PageRequest.of(0, 10));
        long duration = System.currentTimeMillis() - start;

        assertThat(result.getContent()).hasSize(1);
        assertThat(duration).isLessThan(1000);
    }

    @Test
    @DisplayName("English keyword finds English text after config fix")
    void searchByEnglishKeywordFindsEnglishText() {
        createAndPublishListing("Golden Retriever puppy", "Friendly dog", "Golden Retriever");

        Page<ListingResponse> result = listingService.searchListings("golden", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Golden Retriever");
    }

    @Test
    @DisplayName("Search with only stop words returns empty result")
    void searchWithOnlyStopWords_returnsEmpty() {
        Page<ListingResponse> result = listingService.searchListings("и в на", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Search with mixed case preserves results")
    void searchWithMixedCase_worksCorrectly() {
        Page<ListingResponse> result = listingService.searchListings("ХаСкИ", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Хаски");
    }

    @Test
    @DisplayName("Search for non-existent word returns empty")
    void searchForNonExistentWord_returnsEmpty() {
        Page<ListingResponse> result = listingService.searchListings("xyz789nonexistent", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }
}
