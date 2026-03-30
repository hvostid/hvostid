package ru.hvostid.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response body containing full user profile data including roles.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        Long id,
        String email,
        String name,
        List<String> roles,
        String phone,
        String city,
        String bio,
        Double rating
) {
}
