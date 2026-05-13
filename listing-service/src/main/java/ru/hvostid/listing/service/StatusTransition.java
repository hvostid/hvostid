package ru.hvostid.listing.service;

import java.util.Set;
import ru.hvostid.listing.entity.ListingStatus;

/**
 * @param allowedRoles SELLER, MODERATOR, ADMIN
 */
public record StatusTransition(
        ListingStatus from, ListingStatus to, Set<String> allowedRoles, boolean requiresComment, boolean ownerOnly) {}
