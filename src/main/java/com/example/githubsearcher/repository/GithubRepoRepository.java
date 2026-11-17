package com.example.githubsearcher.repository;

import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import java.util.List;

public interface GithubRepoRepository {

    void upsertAll(List<GithubRepo> repos);

    List<GithubRepo> findAll(RepositoryFilter filter);
}
