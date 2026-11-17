package com.example.githubsearcher.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.githubsearcher.support.EmbeddedPostgresConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureMockRestServiceServer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMockRestServiceServer
@Import(EmbeddedPostgresConfig.class)
class GithubRepoApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MockRestServiceServer github;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanDb() {
        jdbc.execute("truncate table repositories");
    }

    @Test
    void searchFetchesFromGithubSavesAndReturnsRepositories() throws Exception {
        github.expect(requestTo("https://api.github.com/search/repositories"
                        + "?q=spring%20boot%20in%3Aname%20language%3A%22Java%22&sort=stars&order=desc&per_page=100"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
                .andRespond(withSuccess(githubJson(item(123456, "spring-boot-example", "Java", 450, 120, "2024-01-01T12:00:00Z")),
                        MediaType.APPLICATION_JSON));

        search("""
                {"query": "spring boot", "language": "Java", "sort": "stars"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Repositories fetched and saved successfully"))
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].name").value("spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].fullName").value("user123/spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].description").value("An example repository"))
                .andExpect(jsonPath("$.repositories[0].owner").value("user123"))
                .andExpect(jsonPath("$.repositories[0].language").value("Java"))
                .andExpect(jsonPath("$.repositories[0].stars").value(450))
                .andExpect(jsonPath("$.repositories[0].forks").value(120))
                .andExpect(jsonPath("$.repositories[0].openIssues").value(7))
                .andExpect(jsonPath("$.repositories[0].url").value("https://github.com/user123/spring-boot-example"))
                .andExpect(jsonPath("$.repositories[0].lastUpdated").value("2024-01-01T12:00:00Z"));
        github.verify();

        mvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.repositories[0].id").value(123456))
                .andExpect(jsonPath("$.repositories[0].owner").value("user123"))
                .andExpect(jsonPath("$.repositories[0].lastUpdated").value("2024-01-01T12:00:00Z"));
    }

    @Test
    void searchWithoutLanguageAndSortDefaultsToStarsAndOmitsLanguageQualifier() throws Exception {
        github.expect(requestTo("https://api.github.com/search/repositories"
                        + "?q=tetris%20in%3Aname&sort=stars&order=desc&per_page=100"))
                .andRespond(withSuccess(githubJson(), MediaType.APPLICATION_JSON));

        search("""
                {"query": "  tetris "}""").andExpect(status().isOk());
        github.verify();
    }

    @Test
    void searchingAgainUpdatesExistingRepositoryInsteadOfDuplicating() throws Exception {
        stubGithub(item(1, "repo", "Java", 10, 1, "2024-01-01T00:00:00Z"));
        search("""
                {"query": "repo"}""").andExpect(status().isOk());

        github.reset();
        stubGithub(item(1, "repo-renamed", "Kotlin", 99, 5, "2025-06-01T00:00:00Z"));
        search("""
                {"query": "repo"}""").andExpect(status().isOk());

        mvc.perform(get("/api/github/repositories"))
                .andExpect(jsonPath("$.repositories", hasSize(1)))
                .andExpect(jsonPath("$.repositories[0].name").value("repo-renamed"))
                .andExpect(jsonPath("$.repositories[0].language").value("Kotlin"))
                .andExpect(jsonPath("$.repositories[0].stars").value(99))
                .andExpect(jsonPath("$.repositories[0].forks").value(5))
                .andExpect(jsonPath("$.repositories[0].lastUpdated").value("2025-06-01T00:00:00Z"));
    }

    @Test
    void searchHandlesNullableGithubFields() throws Exception {
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withSuccess("""
                        {"items": [{"id": 5, "name": "bare", "description": null, "language": null,
                                    "owner": null, "stargazers_count": 0, "forks_count": 0}]}""",
                        MediaType.APPLICATION_JSON));

        search("""
                {"query": "bare"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[0].id").value(5))
                .andExpect(jsonPath("$.repositories[0].owner").doesNotExist())
                .andExpect(jsonPath("$.repositories[0].language").doesNotExist());
        mvc.perform(get("/api/github/repositories")).andExpect(jsonPath("$.repositories", hasSize(1)));
    }

    @Test
    void searchWithNoResultsReturnsEmptyList() throws Exception {
        stubGithub();

        search("""
                {"query": "nothing-matches-this"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No repositories found"))
                .andExpect(jsonPath("$.repositories", hasSize(0)));
    }

    @Test
    void githubPrimaryRateLimitIsReportedAs429() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ratelimit-remaining", "0");
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers).body("{\"message\":\"API rate limit exceeded\"}"));

        search("""
                {"query": "spring"}""")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail", containsString("rate limit")));
    }

    @Test
    void githubSecondaryRateLimitIsReportedAs429() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("retry-after", "60");
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));
        search("""
                {"query": "spring"}""").andExpect(status().isTooManyRequests());

        github.reset();
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        search("""
                {"query": "spring"}""").andExpect(status().isTooManyRequests());
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 422, 500, 503})
    void otherGithubErrorsAreReportedAs502(int githubStatus) throws Exception {
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withStatus(HttpStatus.valueOf(githubStatus)));

        search("""
                {"query": "spring"}""")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail", containsString(String.valueOf(githubStatus))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json at all", "{\"items\": \"oops\"}", "{}", "{\"items\": null}"})
    void invalidGithubResponseIsReportedAs502AndNothingIsSaved(String body) throws Exception {
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        search("""
                {"query": "spring"}""")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail", containsString("invalid response")));
        mvc.perform(get("/api/github/repositories")).andExpect(jsonPath("$.repositories", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"query\": \"   \"}",
            "{\"query\": \"x\", \"sort\": \"name\"}",
            "{\"query\": ",   // malformed JSON
            ""                // missing body
    })
    void invalidSearchRequestIsRejectedWith400WithoutCallingGithub(String body) throws Exception {
        search(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
        github.verify(); // we never told the stub to expect a call, so this fails if GitHub was called
    }

    @Test
    void validationErrorsExplainWhatIsWrong() throws Exception {
        search("""
                {"query": "", "sort": "name"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("query must not be blank")))
                .andExpect(jsonPath("$.detail", containsString("sort must be one of: stars, forks, updated")));
    }

    @Test
    void repositoriesAreSortedByStarsByDefault() throws Exception {
        seed();
        mvc.perform(get("/api/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories[*].id", contains(2, 3, 1)));
    }

    @Test
    void repositoriesCanBeSortedByForksOrUpdatedDate() throws Exception {
        seed();
        mvc.perform(get("/api/github/repositories?sort=forks"))
                .andExpect(jsonPath("$.repositories[*].id", contains(1, 2, 3)));
        mvc.perform(get("/api/github/repositories?sort=updated"))
                .andExpect(jsonPath("$.repositories[*].id", contains(3, 1, 2)));
        mvc.perform(get("/api/github/repositories?sort=stars"))
                .andExpect(jsonPath("$.repositories[*].id", contains(2, 3, 1)));
    }

    @Test
    void repositoriesCanBeFilteredByLanguageCaseInsensitively() throws Exception {
        seed();
        mvc.perform(get("/api/github/repositories?language=java"))
                .andExpect(jsonPath("$.repositories[*].id", contains(2, 1)));
        mvc.perform(get("/api/github/repositories?language=Cobol"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositories", hasSize(0)));
    }

    @Test
    void repositoriesCanBeFilteredByMinimumStars() throws Exception {
        seed();
        mvc.perform(get("/api/github/repositories?minStars=200"))
                .andExpect(jsonPath("$.repositories[*].id", contains(2, 3)));
        mvc.perform(get("/api/github/repositories?minStars=500"))
                .andExpect(jsonPath("$.repositories[*].id", contains(2)));
    }

    @Test
    void filtersAndSortCombine() throws Exception {
        seed();
        mvc.perform(get("/api/github/repositories?language=Java&minStars=100&sort=forks"))
                .andExpect(jsonPath("$.repositories[*].id", contains(1, 2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sort=name", "minStars=-1", "minStars=abc", "sort=stars;drop table repositories"})
    void invalidFiltersAreRejectedWith400(String queryString) throws Exception {
        mvc.perform(get("/api/github/repositories?" + queryString))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private ResultActions search(String body) throws Exception {
        return mvc.perform(post("/api/github/search").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void stubGithub(String... items) {
        github.expect(requestTo(containsString("/search/repositories")))
                .andRespond(withSuccess(githubJson(items), MediaType.APPLICATION_JSON));
    }

    // Test data, picked so each sort gives a different order:
    //   1: Java,   100 stars, 50 forks, updated 2024
    //   2: Java,   500 stars, 10 forks, updated 2023
    //   3: Python, 300 stars,  5 forks, updated 2025
    private void seed() throws Exception {
        stubGithub(item(1, "a", "Java", 100, 50, "2024-01-01T00:00:00Z"),
                item(2, "b", "Java", 500, 10, "2023-01-01T00:00:00Z"),
                item(3, "c", "Python", 300, 5, "2025-01-01T00:00:00Z"));
        search("""
                {"query": "seed"}""").andExpect(status().isOk());
    }

    private static String githubJson(String... items) {
        return "{\"total_count\": %d, \"incomplete_results\": false, \"items\": [%s]}"
                .formatted(items.length, String.join(",", items));
    }

    private static String item(long id, String name, String language, int stars, int forks, String updatedAt) {
        return """
                {"id": %d, "node_id": "x", "name": "%s", "full_name": "user123/%s", "private": false,
                 "owner": {"login": "user123", "id": 1}, "html_url": "https://github.com/user123/%s",
                 "description": "An example repository", "language": "%s", "stargazers_count": %d,
                 "watchers_count": %d, "forks_count": %d, "open_issues_count": 7, "updated_at": "%s"}"""
                .formatted(id, name, name, name, language, stars, stars, forks, updatedAt);
    }
}
