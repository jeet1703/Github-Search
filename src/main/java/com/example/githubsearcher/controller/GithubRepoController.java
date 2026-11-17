package com.example.githubsearcher.controller;

import com.example.githubsearcher.dto.RepositoryListResponse;
import com.example.githubsearcher.dto.SearchRequest;
import com.example.githubsearcher.dto.SearchResponse;
import com.example.githubsearcher.mapper.GithubRepoMapper;
import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import com.example.githubsearcher.model.SortField;
import com.example.githubsearcher.service.GithubRepoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/github")
@Tag(name = "GitHub Repositories", description = "Search GitHub and query stored repositories")
public class GithubRepoController {

    private final GithubRepoService service;
    private final GithubRepoMapper mapper;

    public GithubRepoController(GithubRepoService service, GithubRepoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping("/search")
    @Operation(summary = "Search GitHub repositories and save the results",
            description = "Fetches repositories whose name matches the query from GitHub and upserts them.")
    @ApiResponse(responseCode = "200", description = "Fetched and saved (the list may be empty)")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @ApiResponse(responseCode = "429", description = "GitHub rate limit exceeded")
    @ApiResponse(responseCode = "502", description = "GitHub failed or returned an invalid response")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        List<GithubRepo> repos = service.searchAndSave(
                request.query(), request.language(), SortField.fromValue(request.sort()));
        return SearchResponse.of(mapper.toResponses(repos));
    }

    @GetMapping("/repositories")
    @Operation(summary = "Get stored repositories", description = "All filters are optional.")
    @ApiResponse(responseCode = "200", description = "Matching stored repositories")
    @ApiResponse(responseCode = "400", description = "Invalid filter")
    public RepositoryListResponse getRepositories(
            @Parameter(description = "Programming language (case-insensitive)", example = "Java")
            @RequestParam(required = false)
            @Size(max = 50, message = "language must be at most 50 characters") String language,

            @Parameter(description = "Minimum stars count (inclusive)", example = "100")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "minStars must not be negative") int minStars,

            @Parameter(description = "Sort order (descending): stars, forks or updated", example = "stars")
            @RequestParam(defaultValue = "stars")
            @Pattern(regexp = SortField.PATTERN, message = SortField.VALIDATION_MESSAGE) String sort) {
        RepositoryFilter filter = new RepositoryFilter(language, minStars, SortField.fromValue(sort));
        return new RepositoryListResponse(mapper.toResponses(service.findRepositories(filter)));
    }
}
