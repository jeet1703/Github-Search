package com.example.githubsearcher.dto;

import java.util.List;

public record SearchResponse(String message, List<RepositoryResponse> repositories) {

    public static final String SUCCESS = "Repositories fetched and saved successfully";
    public static final String NO_RESULTS = "No repositories found";

    public static SearchResponse of(List<RepositoryResponse> repositories) {
        return new SearchResponse(repositories.isEmpty() ? NO_RESULTS : SUCCESS, repositories);
    }
}
