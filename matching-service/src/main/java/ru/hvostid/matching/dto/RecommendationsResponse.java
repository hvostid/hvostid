package ru.hvostid.matching.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Paginated buyer recommendations sorted by compatibility score (descending)")
public record RecommendationsResponse(
        @Schema(description = "Items on the requested page") List<RecommendationItem> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "10")
        int size,

        @Schema(description = "Total number of recommendations after minScore filtering", example = "42")
        long totalElements,

        @Schema(description = "Total page count", example = "5")
        int totalPages) {}
