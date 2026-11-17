package com.example.githubsearcher.model;

import java.time.OffsetDateTime;

public record GithubRepo(
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
