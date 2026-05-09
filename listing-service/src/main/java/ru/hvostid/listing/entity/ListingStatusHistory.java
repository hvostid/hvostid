package ru.hvostid.listing.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity
@Table(name = "listing_status_history")
public class ListingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "from_status")
    @Enumerated(EnumType.STRING)
    private ListingStatus fromStatus;

    @Column(name = "to_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private ListingStatus toStatus;

    @Column(name = "changed_by_user_id", nullable = false)
    private Long changedByUserId;

    @Column(name = "changed_by_role")
    private String changedByRole;  // "SELLER", "MODERATOR", "ADMIN"

    private String comment;  // moderators comment

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public ListingStatusHistory(Long listingId, ListingStatus fromStatus,
                                ListingStatus toStatus, Long changedByUserId,
                                String changedByRole, String comment) {
        this.listingId = listingId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedByRole = changedByRole;
        this.comment = comment;
    }

    protected ListingStatusHistory() {}

    // getters (setters not needed as history is read-only)
    public Long getId() { return id; }
    public Long getListingId() { return listingId; }
    public ListingStatus getFromStatus() { return fromStatus; }
    public ListingStatus getToStatus() { return toStatus; }
    public Long getChangedByUserId() { return changedByUserId; }
    public String getChangedByRole() { return changedByRole; }
    public String getComment() { return comment; }
    public Instant getChangedAt() { return changedAt; }
}
