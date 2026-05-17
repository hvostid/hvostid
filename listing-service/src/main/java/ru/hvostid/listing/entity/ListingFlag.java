package ru.hvostid.listing.entity;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
        name = "listing_flags",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_listing_flags_listing_reporter",
                        columnNames = {"listing_id", "reporter_id"}))
public class ListingFlag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FlagReason reason;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private FlagStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ListingFlag() {}

    public ListingFlag(Long listingId, Long reporterId, FlagReason reason, String description) {
        this.listingId = listingId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.description = description;
        this.status = FlagStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public Long getListingId() {
        return listingId;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public FlagReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public FlagStatus getStatus() {
        return status;
    }

    public void setStatus(FlagStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
