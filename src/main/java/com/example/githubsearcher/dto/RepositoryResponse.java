package com.example.githubsearcher.dto;

import java.time.OffsetDateTime;

public record RepositoryResponse(
        long id,
        String name,
        String fullName,
        String description,
        String owner,
        String language,
        int stars,
        int forks,
        int openIssues,
        String url,
        OffsetDateTime lastUpdated) {
}
