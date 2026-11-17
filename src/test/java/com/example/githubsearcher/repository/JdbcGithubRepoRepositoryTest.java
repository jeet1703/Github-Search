package com.example.githubsearcher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.support.EmbeddedPostgresConfig;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EmbeddedPostgresConfig.class, JdbcGithubRepoRepository.class})
class JdbcGithubRepoRepositoryTest {

    @Autowired
    private GithubRepoRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void upsertInsertsNewRepositories() {
        repository.upsertAll(List.of(repo(1, "Java", 10, 1, "2024-01-01T00:00:00Z")));

        assertThat(repository.findAll(new RepositoryFilter(null, 0, SortField.STARS)))
                .containsExactly(repo(1, "Java", 10, 1, "2024-01-01T00:00:00Z"));
    }

    @Test
    void upsertUpdatesExistingRepositoryInsteadOfDuplicating() {
        repository.upsertAll(List.of(repo(1, "Java", 10, 1, "2024-01-01T00:00:00Z")));
        repository.upsertAll(List.of(repo(1, "Kotlin", 99, 5, "2025-06-01T00:00:00Z")));

        assertThat(jdbc.queryForObject("select count(*) from repositories", Integer.class)).isEqualTo(1);
        assertThat(repository.findAll(new RepositoryFilter(null, 0, SortField.STARS)))
                .containsExactly(repo(1, "Kotlin", 99, 5, "2025-06-01T00:00:00Z"));
    }

    @Test
    void upsertOfEmptyListIsANoOp() {
        repository.upsertAll(List.of());
        assertThat(jdbc.queryForObject("select count(*) from repositories", Integer.class)).isZero();
    }

    @Test
    void upsertHandlesDuplicatesAcrossOneBatchAndNullableColumns() {
        repository.upsertAll(List.of(
                new GithubRepo(7, "bare", null, null, null, null, 0, 0, 0, null, null),
                new GithubRepo(8, "other", null, null, null, null, 1, 0, 0, null, null)));
        assertThat(repository.findAll(new RepositoryFilter(null, 0, SortField.UPDATED)))
                .extracting(GithubRepo::id).containsExactly(7L, 8L);
    }

    @Test
    void filtersByLanguageCaseInsensitivelyAndMinStarsInclusive() {
        seed();
        assertThat(ids(new RepositoryFilter("java", 0, SortField.STARS))).containsExactly(2L, 1L);
        assertThat(ids(new RepositoryFilter(null, 300, SortField.STARS))).containsExactly(2L, 3L);
        assertThat(ids(new RepositoryFilter("Java", 101, SortField.STARS))).containsExactly(2L);
        assertThat(ids(new RepositoryFilter("Cobol", 0, SortField.STARS))).isEmpty();
    }

    @Test
    void sortsDescendingByEachSortField() {
        seed();
        assertThat(ids(new RepositoryFilter(null, 0, SortField.STARS))).containsExactly(2L, 3L, 1L);
        assertThat(ids(new RepositoryFilter(null, 0, SortField.FORKS))).containsExactly(1L, 2L, 3L);
        assertThat(ids(new RepositoryFilter(null, 0, SortField.UPDATED))).containsExactly(3L, 1L, 2L);
    }

    @Test
    void repositoriesWithoutUpdateDateSortLast() {
        seed();
        repository.upsertAll(List.of(new GithubRepo(4, "no-date", null, null, null, "Go", 1, 1, 0, null, null)));
        assertThat(ids(new RepositoryFilter(null, 0, SortField.UPDATED))).containsExactly(3L, 1L, 2L, 4L);
    }

    // Test data, picked so each sort gives a different order:
    //   1: Java,   100 stars, 50 forks, updated 2024
    //   2: Java,   500 stars, 10 forks, updated 2023
    //   3: Python, 300 stars,  5 forks, updated 2025
    private void seed() {
        repository.upsertAll(List.of(
                repo(1, "Java", 100, 50, "2024-01-01T00:00:00Z"),
                repo(2, "Java", 500, 10, "2023-01-01T00:00:00Z"),
                repo(3, "Python", 300, 5, "2025-01-01T00:00:00Z")));
    }

    private List<Long> ids(RepositoryFilter filter) {
        return repository.findAll(filter).stream().map(GithubRepo::id).toList();
    }

    private static GithubRepo repo(long id, String language, int stars, int forks, String updated) {
        return new GithubRepo(id, "repo-" + id, "owner/repo-" + id, "description", "owner", language,
                stars, forks, 2, "https://github.com/owner/repo-" + id, OffsetDateTime.parse(updated));
    }
}
