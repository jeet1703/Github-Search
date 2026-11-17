package com.example.githubsearcher.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.githubsearcher.client.dto.GithubRepoItem;
import com.example.githubsearcher.config.GithubProperties;
import com.example.githubsearcher.exception.GithubApiException;
import com.example.githubsearcher.exception.GithubRateLimitException;
import com.example.githubsearcher.model.SortField;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubRestClientTest {

    private static final String BASE = "https://api.github.com/search/repositories";

    private MockRestServiceServer server;
    private GithubRestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubRestClient(builder.build(), new GithubProperties("https://api.github.com", null, 30));
    }

    @Test
    void buildsNameQueryWithLanguageQualifier() {
        assertThat(GithubRestClient.buildQuery("  spring boot ", " Java "))
                .isEqualTo("spring boot in:name language:\"Java\"");
    }

    @Test
    void omitsLanguageQualifierWhenAbsentAndStripsQuotes() {
        assertThat(GithubRestClient.buildQuery("tetris", null)).isEqualTo("tetris in:name");
        assertThat(GithubRestClient.buildQuery("tetris", " ")).isEqualTo("tetris in:name");
        assertThat(GithubRestClient.buildQuery("x", "C\" OR \"Go")).isEqualTo("x in:name language:\"C OR Go\"");
    }

    @Test
    void sendsSortAndConfiguredPageSizeAndParsesItems() {
        server.expect(requestTo(BASE + "?q=spring%20in%3Aname&sort=forks&order=desc&per_page=30"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"total_count": 1, "incomplete_results": false, "items": [{
                          "id": 42, "name": "spring", "full_name": "acme/spring", "description": "d",
                          "owner": {"login": "acme"}, "language": "Java", "stargazers_count": 5,
                          "forks_count": 3, "open_issues_count": 1, "html_url": "https://github.com/acme/spring",
                          "updated_at": "2024-01-01T12:00:00Z", "unknown_field": true}]}""",
                        MediaType.APPLICATION_JSON));

        List<GithubRepoItem> items = client.searchRepositories("spring", null, SortField.FORKS);

        server.verify();
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(42);
            assertThat(item.fullName()).isEqualTo("acme/spring");
            assertThat(item.owner().login()).isEqualTo("acme");
            assertThat(item.stargazersCount()).isEqualTo(5);
            assertThat(item.forksCount()).isEqualTo(3);
            assertThat(item.openIssuesCount()).isEqualTo(1);
            assertThat(item.htmlUrl()).isEqualTo("https://github.com/acme/spring");
            assertThat(item.updatedAt()).isEqualTo(OffsetDateTime.parse("2024-01-01T12:00:00Z"));
        });
    }

    @Test
    void emptyResultIsAnEmptyList() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withSuccess("{\"total_count\": 0, \"items\": []}", MediaType.APPLICATION_JSON));
        assertThat(client.searchRepositories("none", null, SortField.STARS)).isEmpty();
    }

    @Test
    void rateLimitCarriesRetryAfter() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "60");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.searchRepositories("x", null, SortField.STARS))
                .isInstanceOfSatisfying(GithubRateLimitException.class,
                        e -> assertThat(e.getRetryAfter()).isEqualTo("60"));
    }

    @Test
    void forbiddenWithoutRateLimitHeadersIsAnApiError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.searchRepositories("x", null, SortField.STARS))
                .isExactlyInstanceOf(GithubApiException.class)
                .hasMessageContaining("403");
    }

    @Test
    void networkFailureIsAnApiError() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.searchRepositories("x", null, SortField.STARS))
                .isExactlyInstanceOf(GithubApiException.class)
                .hasMessageContaining("unreachable");
    }

    @Test
    void missingItemsIsAnInvalidResponse() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                .andRespond(withSuccess("{\"message\": \"weird\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.searchRepositories("x", null, SortField.STARS))
                .isExactlyInstanceOf(GithubApiException.class)
                .hasMessageContaining("invalid response");
    }
}
