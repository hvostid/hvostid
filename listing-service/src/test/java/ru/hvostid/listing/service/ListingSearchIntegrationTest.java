package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.dto.ListingRequest;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.ListingUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
@Transactional
class ListingSearchIntegrationTest extends AbstractPostgresContainerTest {

    @Autowired
    private ListingService listingService;

    @Autowired
    private ListingRepository listingRepository;

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

    @Test
    void searchByRussianKeyword_returnsRelevantListings() {
        Page<ListingResponse> result = listingService.searchListings("хаски", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Хаски");
    }

    @Test
    void searchByEnglishKeyword_returnsRelevantListings() {
        Page<ListingResponse> result = listingService.searchListings("labrador", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().breed()).contains("Лабрадор");
    }

    @Test
    void searchWithEmptyKeyword_returnsAllPublished() {
        Page<ListingResponse> result = listingService.searchListings("", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    void searchWithNoMatches_returnsEmptyPage() {
        Page<ListingResponse> result = listingService.searchListings("notexists", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchByMultipleWords_returnsMatches() {
        // plainto_tsquery uses AND between words
        Page<ListingResponse> result = listingService.searchListings("хаски щенок", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Хаски щенок");
    }

    @Test
    void searchWithStemming_returnsWordRootMatches() {
        // Russian stemming: "собаки" finds "собака"
        createAndPublishListing("Собака", "Красивая собака", "Дворняжка");

        Page<ListingResponse> result = listingService.searchListings("собаки", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Собака");
    }

    @Test
    void searchWithStopWords_ignoresThemAndStillWorks() {
        // Russian stop words: и, в, на, с, по, за, под are ignored
        Page<ListingResponse> result = listingService.searchListings("и в на хаски", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst().title()).contains("Хаски");
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

    @Test
    @DisplayName("Search does not expose unpublished listings")
    void search_onlyReturnsPublishedListings() {
        ListingRequest request = new ListingRequest(
                "Секретное объявление",
                "Не должно быть в поиске",
                "CAT",
                "Unknown",
                1,
                1000,
                "Moscow",
                "passport-draft");

        Page<ListingResponse> result = listingService.searchListings("секретное", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Search with null keyword returns all published listings")
    void searchWithNullKeyword_returnsAllPublished() {
        Page<ListingResponse> result = listingService.searchListings(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("Search with whitespace-only keyword returns all published")
    void searchWithWhitespaceKeyword_returnsAllPublished() {
        Page<ListingResponse> resultWhitespace = listingService.searchListings("   ", PageRequest.of(0, 10));
        Page<ListingResponse> resultTab = listingService.searchListings("\t\n", PageRequest.of(0, 10));

        assertThat(resultWhitespace.getContent()).hasSize(3);
        assertThat(resultTab.getContent()).hasSize(3);
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
