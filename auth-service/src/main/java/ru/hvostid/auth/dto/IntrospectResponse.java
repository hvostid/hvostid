package ru.hvostid.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response body for token introspection.
 * When active is false, userId and roles are omitted from JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectResponse(
        boolean active,
        Long userId,
        List<String> roles
) {
    /**
     * Factory for an inactive introspection result.
     */
    public static IntrospectResponse inactive() {
        return new IntrospectResponse(false, null, null);
    }

    /**
     * Factory for an active introspection result.
     */
    public static IntrospectResponse active(Long userId, List<String> roles) {
        return new IntrospectResponse(true, userId, roles);
    }
}
