package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.common.security.UserRole;
import ru.hvostid.listing.dto.FlagListingResponse;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.FlagReason;
import ru.hvostid.listing.entity.FlagStatus;
import ru.hvostid.listing.entity.ListingFlag;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.FlagNotFoundException;
import ru.hvostid.listing.exception.InvalidFlagReviewException;
import ru.hvostid.listing.repository.ListingFlagRepository;
import ru.hvostid.listing.repository.ListingRepository;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    @Mock
    private ListingService listingService;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingFlagRepository flagRepository;

    private ModerationService moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(listingService, listingRepository, flagRepository);
    }

    @Test
    void approveListing_delegatesToUpdateStatusWithPublished() {
        Set<String> roles = Set.of(UserRole.MODERATOR.value());

        moderationService.approveListing(42L, 200L, roles);

        ArgumentCaptor<StatusUpdateRequest> requestCaptor = ArgumentCaptor.forClass(StatusUpdateRequest.class);
        verify(listingService).updateStatus(eq(42L), requestCaptor.capture(), eq(200L), eq(roles));
        assertThat(requestCaptor.getValue().status()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(requestCaptor.getValue().comment()).isNull();
    }

    @Test
    void rejectListing_delegatesToUpdateStatusWithDraftAndComment() {
        Set<String> roles = Set.of(UserRole.MODERATOR.value());

        moderationService.rejectListing(42L, 200L, roles, "Blurry photos");

        ArgumentCaptor<StatusUpdateRequest> requestCaptor = ArgumentCaptor.forClass(StatusUpdateRequest.class);
        verify(listingService).updateStatus(eq(42L), requestCaptor.capture(), eq(200L), eq(roles));
        assertThat(requestCaptor.getValue().status()).isEqualTo(ListingStatus.DRAFT);
        assertThat(requestCaptor.getValue().comment()).isEqualTo("Blurry photos");
    }

    @Test
    void reviewFlag_setsStatusFromPendingToDismissed() {
        ListingFlag flag = new ListingFlag(42L, 5L, FlagReason.SCAM, "fake");
        when(flagRepository.findById(1L)).thenReturn(Optional.of(flag));

        FlagListingResponse response = moderationService.reviewFlag(1L, FlagStatus.DISMISSED);

        assertThat(response.status()).isEqualTo(FlagStatus.DISMISSED);
        assertThat(flag.getStatus()).isEqualTo(FlagStatus.DISMISSED);
        // No explicit save: the dirty managed entity is flushed at @Transactional commit.
        verify(flagRepository, never()).save(any());
    }

    @Test
    void reviewFlag_rejectsPendingDecision() {
        assertThatThrownBy(() -> moderationService.reviewFlag(1L, FlagStatus.PENDING))
                .isInstanceOf(InvalidFlagReviewException.class)
                .hasMessageContaining("REVIEWED or DISMISSED");
        verify(flagRepository, never()).findById(any());
    }

    @Test
    void reviewFlag_rejectsAlreadyReviewedFlag() {
        ListingFlag flag = new ListingFlag(42L, 5L, FlagReason.SCAM, "fake");
        flag.setStatus(FlagStatus.REVIEWED);
        when(flagRepository.findById(1L)).thenReturn(Optional.of(flag));

        assertThatThrownBy(() -> moderationService.reviewFlag(1L, FlagStatus.DISMISSED))
                .isInstanceOf(InvalidFlagReviewException.class)
                .hasMessageContaining("PENDING");
        verify(flagRepository, never()).save(any());
    }

    @Test
    void reviewFlag_throwsWhenFlagMissing() {
        when(flagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moderationService.reviewFlag(99L, FlagStatus.REVIEWED))
                .isInstanceOf(FlagNotFoundException.class);
    }
}
