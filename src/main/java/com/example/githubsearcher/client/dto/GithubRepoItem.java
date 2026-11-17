package com.example.githubsearcher.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.OffsetDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GithubRepoItem(
        long id,
        String name,
        String fullName,
        String description,
        GithubOwner owner,
        String language,
        int stargazersCount,
        int forksCount,
        int openIssuesCount,
        String htmlUrl,
        OffsetDateTime updatedAt) {
}
