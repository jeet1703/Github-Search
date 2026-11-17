package com.example.githubsearcher.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record GithubSearchResponse(Long totalCount, Boolean incompleteResults, List<GithubRepoItem> items) {
}
