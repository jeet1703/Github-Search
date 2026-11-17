package com.example.githubsearcher.mapper;

import com.example.githubsearcher.client.dto.GithubRepoItem;
import com.example.githubsearcher.dto.RepositoryResponse;
import com.example.githubsearcher.model.GithubRepo;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GithubRepoMapper {

    public GithubRepo toDomain(GithubRepoItem item) {
        return new GithubRepo(
                item.id(),
                item.name(),
                item.fullName(),
                item.description(),
                item.owner() == null ? null : item.owner().login(),
                item.language(),
                item.stargazersCount(),
                item.forksCount(),
                item.openIssuesCount(),
                item.htmlUrl(),
                item.updatedAt());
    }

    public RepositoryResponse toResponse(GithubRepo repo) {
        return new RepositoryResponse(
                repo.id(),
                repo.name(),
                repo.fullName(),
                repo.description(),
                repo.owner(),
                repo.language(),
                repo.stars(),
                repo.forks(),
                repo.openIssues(),
                repo.url(),
                repo.lastUpdated());
    }

    public List<RepositoryResponse> toResponses(List<GithubRepo> repos) {
        return repos.stream().map(this::toResponse).toList();
    }
}
