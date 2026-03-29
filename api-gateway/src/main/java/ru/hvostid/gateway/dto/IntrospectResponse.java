package ru.hvostid.gateway.dto;

import java.util.List;

/**
 * Introspection response received from Auth Service.
 * <p>
 * When {@code active} is true, {@code userId} and {@code roles} contain
 * the authenticated user's data. When false, both fields are null.
 */
public record IntrospectResponse(
        boolean active,
        Long userId,
        List<String> roles
) {
}
