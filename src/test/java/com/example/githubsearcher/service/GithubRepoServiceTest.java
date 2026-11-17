package com.example.githubsearcher.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.example.githubsearcher.client.GithubClient;
import com.example.githubsearcher.client.dto.GithubOwner;
import com.example.githubsearcher.client.dto.GithubRepoItem;
import com.example.githubsearcher.exception.GithubApiException;
import com.example.githubsearcher.mapper.GithubRepoMapper;
import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.repository.GithubRepoRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GithubRepoServiceTest {

    private static final OffsetDateTime UPDATED = OffsetDateTime.parse("2024-01-01T12:00:00Z");

    @Mock
    private GithubClient githubClient;
    @Mock
    private GithubRepoRepository repository;

    private GithubRepoService service;

    @BeforeEach
    void setUp() {
        service = new GithubRepoService(githubClient, repository, new GithubRepoMapper());
    }

    @Test
    void searchAndSaveMapsGithubItemsAndUpsertsThem() {
        when(githubClient.searchRepositories("spring boot", "Java", SortField.FORKS)).thenReturn(List.of(
                new GithubRepoItem(1, "spring-boot", "acme/spring-boot", "desc", new GithubOwner("acme"), "Java",
                        450, 120, 7, "https://github.com/acme/spring-boot", UPDATED)));

        List<GithubRepo> result = service.searchAndSave("  spring boot ", " Java ", SortField.FORKS);

        assertThat(result).containsExactly(new GithubRepo(1, "spring-boot", "acme/spring-boot", "desc", "acme",
                "Java", 450, 120, 7, "https://github.com/acme/spring-boot", UPDATED));
        verify(repository).upsertAll(result);
    }

    @Test
    void blankLanguageIsSentAsNoLanguage() {
        when(githubClient.searchRepositories("x", null, SortField.STARS)).thenReturn(List.of());

        assertThat(service.searchAndSave("x", "  ", SortField.STARS)).isEmpty();
        verify(repository).upsertAll(List.of());
    }

    @Test
    void repositoryWithoutOwnerIsMappedWithNullOwner() {
        when(githubClient.searchRepositories("x", null, SortField.STARS)).thenReturn(List.of(
                new GithubRepoItem(2, "x", null, null, null, null, 0, 0, 0, null, null)));

        assertThat(service.searchAndSave("x", null, SortField.STARS))
                .singleElement().extracting(GithubRepo::owner).isNull();
    }

    @Test
    void nothingIsSavedWhenGithubFails() {
        when(githubClient.searchRepositories("x", null, SortField.STARS))
                .thenThrow(new GithubApiException("boom"));

        assertThatThrownBy(() -> service.searchAndSave("x", null, SortField.STARS))
                .isInstanceOf(GithubApiException.class);
        verify(repository, never()).upsertAll(any());
    }

    @Test
    void findRepositoriesDelegatesToRepository() {
        RepositoryFilter filter = new RepositoryFilter("Java", 100, SortField.UPDATED);
        List<GithubRepo> stored = List.of(new GithubRepo(1, "a", null, null, null, "Java", 100, 0, 0, null, null));
        when(repository.findAll(filter)).thenReturn(stored);

        assertThat(service.findRepositories(filter)).isEqualTo(stored);
    }
}
