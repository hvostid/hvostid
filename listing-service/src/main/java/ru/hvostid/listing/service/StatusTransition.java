package ru.hvostid.listing.service;

import java.util.Set;
import ru.hvostid.listing.entity.ListingStatus;

public class StatusTransition {
    private final ListingStatus from;
    private final ListingStatus to;
    private final Set<String> allowedRoles; // SELLER, MODERATOR, ADMIN
    private final boolean requiresComment;
    private final boolean ownerOnly;

    public StatusTransition(
            ListingStatus from,
            ListingStatus to,
            Set<String> allowedRoles,
            boolean requiresComment,
            boolean ownerOnly) {
        this.from = from;
        this.to = to;
        this.allowedRoles = allowedRoles;
        this.requiresComment = requiresComment;
        this.ownerOnly = ownerOnly;
    }

    public ListingStatus getFrom() {
        return from;
    }

    public ListingStatus getTo() {
        return to;
    }

    public Set<String> getAllowedRoles() {
        return allowedRoles;
    }

    public boolean isRequiresComment() {
        return requiresComment;
    }

    public boolean isOwnerOnly() {
        return ownerOnly;
    }
}
