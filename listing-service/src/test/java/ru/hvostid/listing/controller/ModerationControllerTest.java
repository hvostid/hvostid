package ru.hvostid.listing.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import ru.hvostid.listing.entity.FlagReason;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingFlag;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ModerationControllerTest extends AbstractPostgresContainerTest {
    private static final String BASE = "/api/v1/moderation";
    private static final Long OWNER_ID = 100L;
    private static final Long MODERATOR_ID = 200L;
    private static final Long ADMIN_ID = 300L;
    private static final Long BUYER_ID = 400L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ListingRepository listingRepository;

    @Autowired
    private ListingFlagRepository flagRepository;

    private Long inModerationId;

    @BeforeEach
    void seed() {
        flagRepository.deleteAll();
        listingRepository.deleteAll();

        Listing inModeration = save("Under review", ListingStatus.MODERATION);
        save("Already published", ListingStatus.PUBLISHED);
        save("Still draft", ListingStatus.DRAFT);
        inModerationId = inModeration.getId();

        ListingFlag pendingFlag = new ListingFlag(inModerationId, BUYER_ID, FlagReason.SCAM, "Suspicious pricing");
        flagRepository.save(pendingFlag);
    }

    @Test
    @DisplayName("buyer gets 403 for moderation listings")
    void buyer_forbidden() throws Exception {
        mockMvc.perform(get(BASE + "/listings").header(USER_ID, BUYER_ID).header(USER_ROLES, UserRole.BUYER.value()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("moderator sees listings in MODERATION only")
    void moderator_listsModerationOnly() throws Exception {
        mockMvc.perform(get(BASE + "/listings")
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(inModerationId.intValue())))
                .andExpect(jsonPath("$.content[0].status", is("MODERATION")));
    }

    @Test
    @DisplayName("listing detail returns flags ordered by createdAt desc")
    void detail_includesFlags() throws Exception {
        mockMvc.perform(get(BASE + "/listings/{id}", inModerationId)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listing.id", is(inModerationId.intValue())))
                .andExpect(jsonPath("$.flags", hasSize(1)))
                .andExpect(jsonPath("$.flags[0].reason", is("SCAM")));
    }

    @Test
    @DisplayName("approve transitions MODERATION -> PUBLISHED")
    void approve_publishesListing() throws Exception {
        mockMvc.perform(post(BASE + "/listings/{id}/approve", inModerationId)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(inModerationId.intValue())))
                .andExpect(jsonPath("$.status", is("PUBLISHED")));
    }

    @Test
    @DisplayName("reject returns listing to DRAFT with comment")
    void reject_returnsToDraft() throws Exception {
        mockMvc.perform(post(BASE + "/listings/{id}/reject", inModerationId)
                        .header(USER_ID, ADMIN_ID)
                        .header(USER_ROLES, UserRole.ADMIN.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\": \"Photos are blurry\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DRAFT")));
    }

    @Test
    @DisplayName("reject without comment returns 400")
    void reject_missingComment_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/listings/{id}/reject", inModerationId)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pending flags list")
    void pendingFlags_listed() throws Exception {
        mockMvc.perform(get(BASE + "/flags")
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status", is("PENDING")));
    }

    @Test
    @DisplayName("review a flag transitions PENDING -> DISMISSED")
    void review_dismissesFlag() throws Exception {
        Long flagId =
                flagRepository.findAll().stream().findFirst().orElseThrow().getId();

        mockMvc.perform(post(BASE + "/flags/{id}/review", flagId)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"DISMISSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DISMISSED")));
    }

    @Test
    @DisplayName("review with PENDING decision returns 400")
    void review_invalidDecision_returns400() throws Exception {
        Long flagId =
                flagRepository.findAll().stream().findFirst().orElseThrow().getId();

        mockMvc.perform(post(BASE + "/flags/{id}/review", flagId)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"PENDING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("review missing flag returns 404")
    void review_missingFlag_returns404() throws Exception {
        mockMvc.perform(post(BASE + "/flags/{id}/review", 999_999L)
                        .header(USER_ID, MODERATOR_ID)
                        .header(USER_ROLES, UserRole.MODERATOR.value())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"DISMISSED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("unauthenticated request returns 401")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/listings")).andExpect(status().isUnauthorized());
    }

    private Listing save(String title, ListingStatus status) {
        Listing listing = Listing.builder()
                .sellerId(OWNER_ID)
                .title(title)
                .description("Desc")
                .species("dog")
                .breed("Labrador")
                .age(6)
                .price(15000)
                .city("Moscow")
                .passportId("p-" + title.hashCode())
                .build();
        listing.setStatus(status);
        return listingRepository.save(listing);
    }
}
