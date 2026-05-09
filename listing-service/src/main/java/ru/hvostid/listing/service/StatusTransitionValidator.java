package ru.hvostid.listing.service;

import ru.hvostid.listing.entity.ListingStatus;
import ru.hvostid.listing.exception.AccessDeniedException;
import ru.hvostid.listing.exception.InvalidStatusTransitionException;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StatusTransitionValidator {

    // Terminal states - from these you cannot transition to any other state
    private static final Set<ListingStatus> TERMINAL_STATES = Set.of(
            ListingStatus.ARCHIVED,
            ListingStatus.SOLD
    );

    // Allowed transitions (from -> Set of possible transitions)
    private static final Map<ListingStatus, Set<StatusTransition>> ALLOWED_TRANSITIONS = new EnumMap<>(ListingStatus.class);

    static {
        // DRAFT -> MODERATION (owner only)
        addRule(ListingStatus.DRAFT, ListingStatus.MODERATION,
                Set.of(), false, true);

        // MODERATION -> PUBLISHED (moderator or admin)
        addRule(ListingStatus.MODERATION, ListingStatus.PUBLISHED,
                Set.of("MODERATOR", "ADMIN"), false, false);

        // MODERATION -> REJECTED (moderator or admin)
        addRule(ListingStatus.MODERATION, ListingStatus.REJECTED,
                Set.of("MODERATOR", "ADMIN"), false, false);

        // MODERATION -> DRAFT (moderator or admin, requires comment)
        addRule(ListingStatus.MODERATION, ListingStatus.DRAFT,
                Set.of("MODERATOR", "ADMIN"), true, false);

        // PUBLISHED -> ARCHIVED (owner only)
        addRule(ListingStatus.PUBLISHED, ListingStatus.ARCHIVED,
                Set.of(), false, true);

        // PUBLISHED -> SOLD (owner only)
        addRule(ListingStatus.PUBLISHED, ListingStatus.SOLD,
                Set.of(), false, true);

        // REJECTED -> DRAFT (owner can edit and resubmit)
        addRule(ListingStatus.REJECTED, ListingStatus.DRAFT,
                Set.of(), false, true);
    }

    private static void addRule(ListingStatus from, ListingStatus to,
                                Set<String> allowedRoles,
                                boolean requiresComment,
                                boolean ownerOnly) {
        ALLOWED_TRANSITIONS.computeIfAbsent(from, k -> new HashSet<>())
                .add(new StatusTransition(from, to, allowedRoles, requiresComment, ownerOnly));
    }

    /**
     * Validates if transition from current status to new status is allowed.
     *
     * @param currentStatus current listing status
     * @param newStatus requested new status
     * @return StatusTransition rule if valid
     * @throws InvalidStatusTransitionException if transition is not allowed
     */
    public static StatusTransition validateTransition(ListingStatus currentStatus, ListingStatus newStatus) {
        // Check if current status is terminal
        if (TERMINAL_STATES.contains(currentStatus)) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot change status from terminal state: %s", currentStatus)
            );
        }

        // Check if same status
        if (currentStatus == newStatus) {
            throw new InvalidStatusTransitionException(
                    String.format("Listing is already in status: %s", currentStatus)
            );
        }

        // Find transition rule
        Set<StatusTransition> transitions = ALLOWED_TRANSITIONS.get(currentStatus);
        if (transitions == null) {
            throw new InvalidStatusTransitionException(
                    String.format("No transitions defined from status: %s", currentStatus)
            );
        }

        StatusTransition transition = transitions.stream()
                .filter(t -> t.getTo() == newStatus)
                .findFirst()
                .orElse(null);

        if (transition == null) {
            throw new InvalidStatusTransitionException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }

        return transition;
    }

    /**
     * Checks if the user has permission to perform the transition.
     *
     * @param transition transition rule to check
     * @param isOwner whether the user is the owner of the listing
     * @param userRoles set of user's roles (SELLER, MODERATOR, ADMIN, etc.)
     * @throws AccessDeniedException if user lacks permission
     */
    public static void checkPermissions(StatusTransition transition, boolean isOwner, Set<String> userRoles) {
        // Check if transition is owner-only
        if (transition.isOwnerOnly()) {
            if (!isOwner) {
                throw new AccessDeniedException(
                        String.format("Only the owner can change status from %s to %s",
                                transition.getFrom(), transition.getTo())
                );
            }
            return;
        }

        // Check role-based access
        Set<String> allowedRoles = transition.getAllowedRoles();
        boolean hasRequiredRole = allowedRoles.stream().anyMatch(role ->
                userRoles.contains(role) || (role.equals("ADMIN") && userRoles.contains("ADMIN"))
        );

        if (!hasRequiredRole) {
            throw new AccessDeniedException(
                    String.format("Required roles for transition from %s to %s: %s. User roles: %s",
                            transition.getFrom(), transition.getTo(), allowedRoles, userRoles)
            );
        }
    }

    /**
     * Checks if a comment is required for this transition.
     */
    public static boolean isCommentRequired(StatusTransition transition) {
        return transition.isRequiresComment();
    }
}
