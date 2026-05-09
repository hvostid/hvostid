package ru.hvostid.listing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.hvostid.listing.dto.ListingResponse;
import ru.hvostid.listing.dto.StatusUpdateRequest;
import ru.hvostid.listing.entity.Listing;
import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.entity.ListingStatusHistory;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.InvalidListingStatusException;
import ru.hvostid.listing.exception.InvalidStatusTransitionException;
import ru.hvostid.listing.exception.ListingNotFoundException;
import ru.hvostid.listing.repository.ListingRepository;
import ru.hvostid.listing.repository.ListingStatusHistoryRepository;

@ExtendWith(MockitoExtension.class)
class ListingServiceStatusTransitionTest {

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private ListingStatusHistoryRepository historyRepository;

    @InjectMocks
    private ListingService listingService;

    private Listing listing;
    private final Long LISTING_ID = 1L;
    private final Long OWNER_ID = 100L;
    private final Long MODERATOR_ID = 200L;
    private final Long ADMIN_ID = 300L;
    private final Long OTHER_USER_ID = 999L;

    @BeforeEach
    void setUp() {
        listing = new Listing(
                OWNER_ID, "Test Listing", "Description", "Dog", "Labrador", 12, 500, "Moscow", "PASSPORT123");
        listing.setId(LISTING_ID);
    }

    // ============ VALID TRANSITIONS ============

    @Test
    void shouldAllowOwnerToSendToModeration() {
        // given
        listing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.MODERATION, null);

        // when
        ListingResponse response = listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.MODERATION);
        assertThat(response.status()).isEqualTo(ListingStatus.MODERATION);
        verify(historyRepository).save(any(ListingStatusHistory.class));
    }

    @Test
    void shouldAllowModeratorToPublish() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.PUBLISHED, null);

        // when
        listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
        verify(historyRepository).save(any());
    }

    @Test
    void shouldAllowModeratorToReject() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.REJECTED, "Poor quality photos");

        // when
        listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.REJECTED);
        assertThat(listing.getModerationComment()).isEqualTo("Poor quality photos");
        verify(historyRepository).save(any());
    }

    @Test
    void shouldAllowModeratorToReturnToDraftWithComment() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        String comment = "Please fix the description and add more photos";
        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.DRAFT, comment);

        // when
        listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(listing.getModerationComment()).isEqualTo(comment);
        verify(historyRepository).save(any());
    }

    @Test
    void shouldAllowAdminToPublish() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.PUBLISHED, null);

        // when
        listingService.updateStatus(LISTING_ID, request, ADMIN_ID, Set.of("ADMIN"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
    }

    @Test
    void shouldAllowOwnerToArchive() {
        // given
        listing.setStatus(ListingStatus.PUBLISHED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.ARCHIVED, null);

        // when
        listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ARCHIVED);
    }

    @Test
    void shouldAllowOwnerToMarkAsSold() {
        // given
        listing.setStatus(ListingStatus.PUBLISHED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.SOLD, null);

        // when
        listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.SOLD);
    }

    @Test
    void shouldAllowOwnerToResubmitFromRejected() {
        // given
        listing.setStatus(ListingStatus.REJECTED);
        listing.setModerationComment("Poor quality");
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.DRAFT, null);

        // when
        listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER"));

        // then
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(listing.getModerationComment()).isNull(); // Comment cleared
    }

    // ============ INVALID TRANSITIONS (422) ============

    @Test
    void shouldNotAllowDirectDraftToPublished() {
        // given
        listing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.PUBLISHED, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid status transition from DRAFT to PUBLISHED");
    }

    @Test
    void shouldNotAllowModerationToSold() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.SOLD, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Invalid status transition from MODERATION to SOLD");
    }

    @Test
    void shouldNotAllowTransitionFromTerminalStateArchived() {
        // given
        listing.setStatus(ListingStatus.ARCHIVED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.DRAFT, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot change status from terminal state: ARCHIVED");
    }

    @Test
    void shouldNotAllowTransitionFromTerminalStateSold() {
        // given
        listing.setStatus(ListingStatus.SOLD);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.ARCHIVED, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Cannot change status from terminal state: SOLD");
    }

    @Test
    void shouldRequireCommentWhenReturningToDraft() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.DRAFT, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR")))
                .isInstanceOf(InvalidListingStatusException.class)
                .hasMessageContaining("Comment is required");
    }

    // ============ ACCESS DENIED (403) ============

    @Test
    void shouldNotAllowNonOwnerToSendToModeration() {
        // given
        listing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.MODERATION, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OTHER_USER_ID, Set.of("SELLER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the owner can change status");
    }

    @Test
    void shouldNotAllowNonModeratorToPublish() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.PUBLISHED, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Required roles for transition");
    }

    @Test
    void shouldNotAllowNonModeratorToReject() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.REJECTED, "Bad");

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void shouldNotAllowNonOwnerToArchive() {
        // given
        listing.setStatus(ListingStatus.PUBLISHED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.ARCHIVED, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OTHER_USER_ID, Set.of("SELLER")))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the owner can change status");
    }

    @Test
    void shouldNotAllowNonOwnerToMarkAsSold() {
        // given
        listing.setStatus(ListingStatus.PUBLISHED);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.SOLD, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OTHER_USER_ID, Set.of("SELLER")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ============ NOT FOUND (404) ============

    @Test
    void shouldThrowNotFoundWhenListingDoesNotExist() {
        // given
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.empty());

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.MODERATION, null);

        // then
        assertThatThrownBy(() -> listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER")))
                .isInstanceOf(ListingNotFoundException.class)
                .hasMessageContaining("Listing not found");
    }

    // ============ HISTORY SAVING ============

    @Test
    void shouldSaveHistoryRecordOnStatusChange() {
        // given
        listing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.MODERATION, null);

        ArgumentCaptor<ListingStatusHistory> historyCaptor = ArgumentCaptor.forClass(ListingStatusHistory.class);

        // when
        listingService.updateStatus(LISTING_ID, request, OWNER_ID, Set.of("SELLER"));

        // then
        verify(historyRepository).save(historyCaptor.capture());
        ListingStatusHistory savedHistory = historyCaptor.getValue();

        assertThat(savedHistory.getListingId()).isEqualTo(LISTING_ID);
        assertThat(savedHistory.getFromStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(savedHistory.getToStatus()).isEqualTo(ListingStatus.MODERATION);
        assertThat(savedHistory.getChangedByUserId()).isEqualTo(OWNER_ID);
        assertThat(savedHistory.getChangedByRole()).isEqualTo("OWNER");
    }

    @Test
    void shouldSaveHistoryWithModeratorRole() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.PUBLISHED, null);

        ArgumentCaptor<ListingStatusHistory> historyCaptor = ArgumentCaptor.forClass(ListingStatusHistory.class);

        // when
        listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR"));

        // then
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getChangedByRole()).isEqualTo("MODERATOR");
    }

    @Test
    void shouldSaveHistoryWithComment() {
        // given
        listing.setStatus(ListingStatus.MODERATION);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(listingRepository.save(any(Listing.class))).thenReturn(listing);

        String comment = "Please add more photos";
        StatusUpdateRequest request = new StatusUpdateRequest(ListingStatus.DRAFT, comment);

        ArgumentCaptor<ListingStatusHistory> historyCaptor = ArgumentCaptor.forClass(ListingStatusHistory.class);

        // when
        listingService.updateStatus(LISTING_ID, request, MODERATOR_ID, Set.of("MODERATOR"));

        // then
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getComment()).isEqualTo(comment);
    }
}
