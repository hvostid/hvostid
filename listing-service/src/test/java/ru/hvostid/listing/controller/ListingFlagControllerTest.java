package ru.hvostid.listing.controller;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.dto.FlagListingRequest;
import ru.hvostid.listing.entity.FlagReason;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListingFlagControllerTest extends AbstractPostgresContainerTest {

    private static final Long OWNER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingFlagRepository flagRepository;

    private Long publishedListingId;

    @BeforeEach
    void setUp() {
        flagRepository.deleteAll();
        listingRepository.deleteAll();

        Listing listing = Listing.builder()
                .sellerId(OWNER_ID)
                .title("Cute Puppy")
                .description("Description")
                .species("dog")
                .breed("Labrador")
                .age(6)
                .price(15000)
                .city("Moscow")
                .passportId("p-1")
                .build();
        listing.setStatus(ListingStatus.PUBLISHED);
        publishedListingId = listingRepository.save(listing).getId();
    }

    @Test
    @DisplayName("authenticated buyer can flag a published listing - returns 201")
    void flag_published_returns201() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, "Suspicious pricing");

        mockMvc.perform(post(flagUrl(publishedListingId))
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.listingId", is(publishedListingId.intValue())))
                .andExpect(jsonPath("$.reporterId", is(200)))
                .andExpect(jsonPath("$.reason", is("SCAM")))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    @DisplayName("repeat flag from the same user - returns 409")
    void flag_duplicateReporter_returns409() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.FAKE_INFO, null);

        mockMvc.perform(post(flagUrl(publishedListingId))
                        .header(USER_ID, 201L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(flagUrl(publishedListingId))
                        .header(USER_ID, 201L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("owner cannot flag their own listing - returns 403")
    void flag_byOwner_returns403() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        mockMvc.perform(post(flagUrl(publishedListingId))
                        .header(USER_ID, OWNER_ID)
                        .header(USER_ROLES, UserRole.SELLER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("flag with missing reason - returns 400")
    void flag_missingReason_returns400() throws Exception {
        mockMvc.perform(post(flagUrl(publishedListingId))
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    @DisplayName("flag a non-published listing - returns 400")
    void flag_draftListing_returns400() throws Exception {
        Listing draft = Listing.builder()
                .sellerId(OWNER_ID)
                .title("Draft Puppy")
                .description("Description")
                .species("dog")
                .breed("Labrador")
                .age(6)
                .price(15000)
                .city("Moscow")
                .passportId("p-2")
                .build();
        draft.setStatus(ListingStatus.DRAFT);
        Long draftId = listingRepository.save(draft).getId();

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        mockMvc.perform(post(flagUrl(draftId))
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("3rd flag auto-moves listing to MODERATION")
    void flag_thirdFlag_autoMovesToModeration() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        for (long reporter = 201L; reporter <= 203L; reporter++) {
            mockMvc.perform(post(flagUrl(publishedListingId))
                            .header(USER_ID, reporter)
                            .header(USER_ROLES, UserRole.BUYER.value())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        Listing reloaded =
                listingRepository.findById(publishedListingId).orElseThrow(() -> new AssertionError("Listing missing"));
        org.assertj.core.api.Assertions.assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.MODERATION);
    }

    @Test
    @DisplayName("unauthenticated request - returns 401")
    void flag_unauthenticated_returns401() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        mockMvc.perform(post(flagUrl(publishedListingId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("flag missing listing - returns 404")
    void flag_missingListing_returns404() throws Exception {
        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        mockMvc.perform(post(flagUrl(999_999L))
                        .header(USER_ID, 200L)
                        .header(USER_ROLES, UserRole.BUYER.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    private static String flagUrl(Long listingId) {
        return "/api/v1/listings/" + listingId + "/flag";
    }
}
