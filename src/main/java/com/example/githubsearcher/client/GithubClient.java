package com.example.githubsearcher.client;

import com.example.githubsearcher.client.dto.GithubRepoItem;
import com.example.githubsearcher.exception.GithubApiException;
import com.example.githubsearcher.exception.GithubRateLimitException;
import com.example.githubsearcher.model.SortField;
import java.util.List;

public interface GithubClient {

    List<GithubRepoItem> searchRepositories(String query, String language, SortField sort);
}
