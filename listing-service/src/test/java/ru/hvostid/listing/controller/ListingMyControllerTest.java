package ru.hvostid.listing.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingMyControllerTest extends AbstractPostgresContainerTest {
    private static final String LISTINGS_URL = "/api/v1/listings";
    private static final Long OWNER_ID = 100L;
    private static final Long OTHER_OWNER_ID = 200L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ListingRepository listingRepository;

    @BeforeEach
    void seed() {
        save("Draft 1", ListingStatus.DRAFT, OWNER_ID);
        save("Published 1", ListingStatus.PUBLISHED, OWNER_ID);
        save("Archived 1", ListingStatus.ARCHIVED, OWNER_ID);
        save("Archived 2", ListingStatus.ARCHIVED, OWNER_ID);
        save("Sold 1", ListingStatus.SOLD, OWNER_ID);
        save("Other's Published", ListingStatus.PUBLISHED, OTHER_OWNER_ID);
        save("Other's Archived", ListingStatus.ARCHIVED, OTHER_OWNER_ID);
    }

    @Test
    @DisplayName("public list shows only PUBLISHED listings, archived hidden")
    void publicList_onlyPublished() throws Exception {
        mockMvc.perform(get(LISTINGS_URL + "?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("PUBLISHED"))));
    }

    @Test
    @DisplayName("?my=true returns all statuses for the caller, including archived/sold")
    void myAll_returnsAllStatuses() throws Exception {
        mockMvc.perform(get(LISTINGS_URL + "?my=true&page=0&size=20")
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[*].sellerId", everyItem(is(OWNER_ID.intValue()))));
    }

    @Test
    @DisplayName("?my=true&status=ARCHIVED returns only archived listings of the caller")
    void myArchived_filters() throws Exception {
        mockMvc.perform(get(LISTINGS_URL + "?my=true&status=ARCHIVED&page=0&size=20")
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ARCHIVED"))))
                .andExpect(jsonPath("$.content[*].sellerId", everyItem(is(OWNER_ID.intValue()))));
    }

    @Test
    @DisplayName("?my=true without auth returns 401")
    void myWithoutAuth_returns401() throws Exception {
        mockMvc.perform(get(LISTINGS_URL + "?my=true")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("?my=true does not leak listings owned by other users")
    void myDoesNotLeakOthers() throws Exception {
        mockMvc.perform(get(LISTINGS_URL + "?my=true&status=PUBLISHED&page=0&size=20")
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Published 1")));
    }

    private void save(String title, ListingStatus status, Long sellerId) {
        Listing listing = Listing.builder()
                .sellerId(sellerId)
                .title(title)
                .description("Desc")
                .species("dog")
                .breed("Labrador")
                .age(3)
                .price(10000)
                .city("Moscow")
                .passportId("p-" + title)
                .build();
        listing.setStatus(status);
        listingRepository.save(listing);
    }
}
