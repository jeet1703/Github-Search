package com.example.githubsearcher.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.githubsearcher.exception.GithubApiException;
import com.example.githubsearcher.exception.GithubRateLimitException;
import com.example.githubsearcher.mapper.GithubRepoMapper;
import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.service.GithubRepoService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(GithubRepoController.class)
@Import(GithubRepoMapper.class)
class GithubRepoControllerTest {

    private static final GithubRepo REPO = new GithubRepo(123456, "spring-boot-example", "user123/spring-boot-example",
            "An example repository for Spring Boot", "user123", "Java", 450, 120, 3,
            "https://github.com/user123/spring-boot-example", OffsetDateTime.parse("2024-01-01T12:00:00Z"));

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private GithubRepoService service;

    @Test
    void searchReturnsSavedRepositoriesInSpecShape() throws Exception {
        when(service.searchAndSave("spring boot", "Java", SortField.STARS)).thenReturn(List.of(REPO));

        search("{\"query\": \"spring boot\", \"language\": \"Java\", \"sort\": \"stars\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].description").value("An example repository for Spring Boot"))
                .andExpect(jsonPath("$.repositories[0].owner").value("user123"))
                .andExpect(jsonPath("$.repositories[0].language").value("Java"))
                .andExpect(jsonPath("$.repositories[0].stars").value(450))
                .andExpect(jsonPath("$.repositories[0].forks").value(120))
                .andExpect(jsonPath("$.repositories[0].lastUpdated").value("2024-01-01T12:00:00Z"));
    }

    @Test
    void searchDefaultsSortToStars() throws Exception {
        when(service.searchAndSave("x", null, SortField.STARS)).thenReturn(List.of());

        search("{\"query\": \"x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No repositories found"))
                .andExpect(jsonPath("$.repositories", hasSize(0)));
    }

    @Test
    void invalidSearchIsRejectedBeforeReachingTheService() throws Exception {
        search("{\"query\": \" \", \"sort\": \"name\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail")
                        .value("query must not be blank; sort must be one of: stars, forks, updated"));
        verifyNoInteractions(service);
    }

    @Test
    void tooLongQueryIsRejected() throws Exception {
        search("{\"query\": \"" + "a".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("query must be at most 200 characters"));
    }

    @Test
    void rateLimitIsMappedTo429WithRetryAfter() throws Exception {
        when(service.searchAndSave(any(), any(), any()))
                .thenThrow(new GithubRateLimitException("GitHub API rate limit exceeded", "30", null));

        search("{\"query\": \"x\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.title").value("GitHub rate limit exceeded"))
                .andExpect(jsonPath("$.detail").value("GitHub API rate limit exceeded"));
    }

    @Test
    void githubErrorIsMappedTo502() throws Exception {
        when(service.searchAndSave(any(), any(), any())).thenThrow(new GithubApiException("GitHub is down"));

        search("{\"query\": \"x\"}")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("GitHub API error"))
                .andExpect(jsonPath("$.detail").value("GitHub is down"));
    }

    @Test
    void unexpectedErrorIsMappedTo500WithoutLeakingDetails() throws Exception {
        when(service.searchAndSave(any(), any(), any())).thenThrow(new IllegalStateException("db password is x"));

        search("{\"query\": \"x\"}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void getRepositoriesBuildsFilterFromParameters() throws Exception {
        RepositoryFilter filter = new RepositoryFilter("Java", 100, SortField.FORKS);
        when(service.findRepositories(filter)).thenReturn(List.of(REPO));

        mvc.perform(get("/api/github/repositories?language=Java&minStars=100&sort=forks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.repositories[0].id").value(123456));
    }

    @Test
    void getRepositoriesUsesDefaults() throws Exception {
        when(service.findRepositories(any())).thenReturn(List.of());

        mvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories", hasSize(0)));
        verify(service).findRepositories(new RepositoryFilter(null, 0, SortField.STARS));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        mvc.perform(get("/api/github/repositories?minStars=-1&sort=name"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("minStars must not be negative")))
                .andExpect(jsonPath("$.detail", containsString("sort must be one of: stars, forks, updated")));
        mvc.perform(get("/api/github/repositories?minStars=abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private ResultActions search(String body) throws Exception {
        return mvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
