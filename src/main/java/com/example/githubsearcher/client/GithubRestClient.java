package com.example.githubsearcher.client;

import com.example.githubsearcher.client.dto.GithubRepoItem;
import com.example.githubsearcher.client.dto.GithubSearchResponse;
import com.example.githubsearcher.config.GithubProperties;
import com.example.githubsearcher.exception.GithubApiException;
import com.example.githubsearcher.exception.GithubRateLimitException;
import com.example.githubsearcher.model.SortField;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GithubRestClient implements GithubClient {

    private static final Logger log = LoggerFactory.getLogger(GithubRestClient.class);
    private static final String SEARCH_URI =
            "/search/repositories?q={q}&sort={sort}&order=desc&per_page={perPage}";

    private final RestClient restClient;
    private final GithubProperties properties;

    public GithubRestClient(RestClient githubHttpClient, GithubProperties properties) {
        this.restClient = githubHttpClient;
        this.properties = properties;
    }

    @Override
    public List<GithubRepoItem> searchRepositories(String query, String language, SortField sort) {
        String q = buildQuery(query, language);
        log.debug("Searching GitHub: q='{}', sort={}", q, sort.value());

        GithubSearchResponse response;
        try {
            response = restClient.get()
                    .uri(SEARCH_URI, q, sort.value(), properties.perPage())
                    .retrieve()
                    .body(GithubSearchResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            throw new GithubApiException("GitHub API is unreachable or returned an invalid response", e);
        }

        if (response == null || response.items() == null) {
            throw new GithubApiException("GitHub API returned an invalid response");
        }
        if (Boolean.TRUE.equals(response.incompleteResults())) {
            log.warn("GitHub returned incomplete results for q='{}' (search timed out on GitHub's side)", q);
        }
        return response.items();
    }

    /**
     * Turns our inputs into GitHub's search syntax, e.g. {@code spring boot in:name language:"Java"}.
     * {@code in:name} makes GitHub match on the repo name only, not the description or README.
     */
    static String buildQuery(String query, String language) {
        StringBuilder q = new StringBuilder(query.trim()).append(" in:name");
        if (language != null && !language.isBlank()) {
            q.append(" language:\"").append(language.trim().replace("\"", "")).append('"');
        }
        return q.toString();
    }

    /**
     * GitHub isn't consistent about rate limits: sometimes it sends a 429, but often it's a 403 with
     * either {@code x-ratelimit-remaining: 0} or a {@code retry-after} header. A plain 403 without
     * those headers is a real error, not a rate limit.
     */
    private static GithubApiException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        HttpHeaders headers = e.getResponseHeaders();
        String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        boolean rateLimited = status == 429 || (status == 403 && headers != null
                && ("0".equals(headers.getFirst("x-ratelimit-remaining")) || retryAfter != null));
        if (rateLimited) {
            return new GithubRateLimitException(
                    "GitHub API rate limit exceeded; retry later or configure GITHUB_TOKEN", retryAfter, e);
        }
        return new GithubApiException("GitHub API returned an error: " + e.getStatusCode(), e);
    }
}
