package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.listing.dto.FlagListingRequest;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.entity.FlagReason;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingFlag;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.entity.ListingStatusHistory;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.DuplicateFlagException;
import ru.hvostid.listing.exception.ListingNotFlaggableException;
import ru.hvostid.listing.exception.ListingNotFoundException;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class ListingFlagServiceTest {

    private static final Long LISTING_ID = 42L;
    private static final Long OWNER_ID = 100L;
    private static final Long REPORTER_ID = 5L;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingFlagRepository flagRepository;

    @Mock
    private ListingStatusHistoryRepository historyRepository;

    @InjectMocks
    private ListingFlagService listingFlagService;

    private Listing publishedListing;

    @BeforeEach
    void setUp() {
        publishedListing = Listing.builder()
                .sellerId(OWNER_ID)
                .title("Test Listing")
                .description("Description")
                .species("dog")
                .breed("Labrador")
                .age(12)
                .price(10000)
                .city("Moscow")
                .passportId("PASSPORT123")
                .build();
        publishedListing.setId(LISTING_ID);
        publishedListing.setStatus(ListingStatus.PUBLISHED);
    }

    @Test
    void shouldCreateFlagForPublishedListing() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));
        when(flagRepository.existsByListingIdAndReporterId(LISTING_ID, REPORTER_ID))
                .thenReturn(false);
        when(flagRepository.save(any(ListingFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flagRepository.countByListingIdAndStatus(LISTING_ID, FlagStatus.PENDING))
                .thenReturn(1L);

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, "Suspicious pricing");

        FlagListingResponse response = listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID);

        assertThat(response.listingId()).isEqualTo(LISTING_ID);
        assertThat(response.reporterId()).isEqualTo(REPORTER_ID);
        assertThat(response.reason()).isEqualTo(FlagReason.SCAM);
        assertThat(response.status()).isEqualTo(FlagStatus.PENDING);
        verify(flagRepository).save(any(ListingFlag.class));
        verify(historyRepository, never()).save(any());
        assertThat(publishedListing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void shouldRejectWhenListingNotFound() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        assertThatThrownBy(() -> listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID))
                .isInstanceOf(ListingNotFoundException.class)
                .hasMessageContaining(LISTING_ID.toString());
        verify(flagRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenReporterIsOwner() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        assertThatThrownBy(() -> listingFlagService.flagListing(LISTING_ID, request, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(flagRepository, never()).save(any());
    }

    @Test
    void shouldRejectWhenListingNotPublished() {
        publishedListing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        assertThatThrownBy(() -> listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID))
                .isInstanceOf(ListingNotFlaggableException.class);
        verify(flagRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateFlagFromSameReporter() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));
        when(flagRepository.existsByListingIdAndReporterId(LISTING_ID, REPORTER_ID))
                .thenReturn(true);

        FlagListingRequest request = new FlagListingRequest(FlagReason.SCAM, null);

        assertThatThrownBy(() -> listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID))
                .isInstanceOf(DuplicateFlagException.class);
        verify(flagRepository, never()).save(any());
    }

    @Test
    void shouldAutoModerateWhenThresholdReached() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));
        when(flagRepository.existsByListingIdAndReporterId(LISTING_ID, REPORTER_ID))
                .thenReturn(false);
        when(flagRepository.save(any(ListingFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flagRepository.countByListingIdAndStatus(LISTING_ID, FlagStatus.PENDING))
                .thenReturn(ListingFlagService.AUTO_MODERATION_THRESHOLD);
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlagListingRequest request = new FlagListingRequest(FlagReason.FAKE_INFO, null);

        listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID);

        assertThat(publishedListing.getStatus()).isEqualTo(ListingStatus.MODERATION);
        verify(listingRepository).save(publishedListing);

        ArgumentCaptor<ListingStatusHistory> historyCaptor = ArgumentCaptor.forClass(ListingStatusHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        ListingStatusHistory history = historyCaptor.getValue();
        assertThat(history.getFromStatus()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(history.getToStatus()).isEqualTo(ListingStatus.MODERATION);
        assertThat(history.getChangedByRole()).isEqualTo(ListingFlagService.SYSTEM_ROLE);
        assertThat(history.getChangedByUserId()).isEqualTo(REPORTER_ID);
    }

    @Test
    void shouldNotAutoModerateBelowThreshold() {
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(publishedListing));
        when(flagRepository.existsByListingIdAndReporterId(LISTING_ID, REPORTER_ID))
                .thenReturn(false);
        when(flagRepository.save(any(ListingFlag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(flagRepository.countByListingIdAndStatus(LISTING_ID, FlagStatus.PENDING))
                .thenReturn(ListingFlagService.AUTO_MODERATION_THRESHOLD - 1);

        FlagListingRequest request = new FlagListingRequest(FlagReason.OTHER, "minor concern");

        listingFlagService.flagListing(LISTING_ID, request, REPORTER_ID);

        assertThat(publishedListing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
        verify(listingRepository, never()).save(any(Listing.class));
        verify(historyRepository, never()).save(any());
    }
}
