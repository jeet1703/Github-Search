package com.example.githubsearcher.dto;

import com.example.githubsearcher.model.SortField;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @Schema(description = "Repository name, partial or full match", example = "spring boot")
        @NotBlank(message = "query must not be blank")
        @Size(max = 200, message = "query must be at most 200 characters")
        String query,

        @Schema(description = "Programming language", example = "Java")
        @Size(max = 50, message = "language must be at most 50 characters")
        String language,

        @Schema(description = "Sort order (descending)", allowableValues = {"stars", "forks", "updated"},
                defaultValue = "stars", example = "stars")
        @Pattern(regexp = SortField.PATTERN, message = SortField.VALIDATION_MESSAGE)
        String sort) {
}
