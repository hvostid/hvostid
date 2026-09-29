package ru.hvostid.listing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.hvostid.common.testfixtures.AbstractPostgresContainerTest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.repository.ListingRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InternalListingControllerTest extends AbstractPostgresContainerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ListingRepository listingRepository;

    @ParameterizedTest
    @EnumSource(ListingStatus.class)
    void deletionGuardAndPublicAccessUseDifferentStatuses(ListingStatus listingStatus) throws Exception {
        for (String passportId : new String[] {"42", "passport-42"}) {
            Listing listing = Listing.builder()
                    .sellerId(10L)
                    .title("Pet " + passportId)
                    .species("CAT")
                    .city("Moscow")
                    .passportId(passportId)
                    .build();
            listing.setStatus(listingStatus);
            listingRepository.saveAndFlush(listing);

            mockMvc.perform(get("/internal/listings/passports/42/has-active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasActiveListing")
                            .value(listingStatus == ListingStatus.MODERATION
                                    || listingStatus == ListingStatus.PUBLISHED));
            mockMvc.perform(get("/api/v1/listings/passports/42/has-published"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasPublishedListing").value(listingStatus == ListingStatus.PUBLISHED));
            listingRepository.delete(listing);
            listingRepository.flush();
        }
    }

    @Test
    void unusedPassportCanBeDeleted() throws Exception {
        mockMvc.perform(get("/internal/listings/passports/999/has-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasActiveListing").value(false));
    }
}
