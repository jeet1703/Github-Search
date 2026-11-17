package com.example.githubsearcher.service;

import com.example.githubsearcher.client.GithubClient;
import com.example.githubsearcher.mapper.GithubRepoMapper;
import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.repository.GithubRepoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GithubRepoService {

    private static final Logger log = LoggerFactory.getLogger(GithubRepoService.class);

    private final GithubClient githubClient;
    private final GithubRepoRepository repository;
    private final GithubRepoMapper mapper;

    public GithubRepoService(GithubClient githubClient, GithubRepoRepository repository, GithubRepoMapper mapper) {
        this.githubClient = githubClient;
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Searches GitHub and saves the results. If GitHub fails, the exception bubbles up and nothing is saved.
     *
     * This method is intentionally not @Transactional. Otherwise we'd hold a database connection the
     * whole time we wait on GitHub, which can be seconds. The save has its own short transaction.
     */
    public List<GithubRepo> searchAndSave(String query, String language, SortField sort) {
        String normalizedLanguage = language == null || language.isBlank() ? null : language.trim();
        List<GithubRepo> repos = githubClient.searchRepositories(query.trim(), normalizedLanguage, sort).stream()
                .map(mapper::toDomain)
                .toList();
        repository.upsertAll(repos);
        log.info("Saved {} repositories for query '{}' (language={}, sort={})",
                repos.size(), query.trim(), normalizedLanguage, sort.value());
        return repos;
    }

    public List<GithubRepo> findRepositories(RepositoryFilter filter) {
        return repository.findAll(filter);
    }
}
