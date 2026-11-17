# GitHub Repository Searcher

Spring Boot service that searches GitHub repositories through the GitHub REST API, stores the
results in PostgreSQL (upsert, no duplicates), and serves the stored results with filters.

**Stack:** Java 17 · Spring Boot 3.5 · PostgreSQL · Flyway · Maven (wrapper included) · springdoc-openapi (Swagger UI) · JUnit 5, Mockito, AssertJ, JaCoCo

---

## Quick start

Prerequisites: Java 17+, and either Docker or an existing PostgreSQL.

```bash
docker compose up -d          # PostgreSQL on localhost:5432 (db "github", user/password postgres/postgres)
./mvnw spring-boot:run        # API on http://localhost:8080
```

Windows: use `mvnw.cmd` instead of `./mvnw`. Maven itself does not need to be installed.

| URL                                      | What                                       |
|------------------------------------------|--------------------------------------------|
| http://localhost:8080/swagger-ui.html    | Swagger UI (interactive API docs)          |
| http://localhost:8080/v3/api-docs        | OpenAPI 3 spec (JSON)                      |
| http://localhost:8080/actuator/health    | Health check (DOWN if the database is down)|

The database schema is created and versioned by Flyway on startup
(`src/main/resources/db/migration`).

### Configuration

Use an existing PostgreSQL by creating an empty database and setting environment variables:

| Variable       | Default                                    | Purpose                                                              |
|----------------|--------------------------------------------|----------------------------------------------------------------------|
| `DB_URL`       | `jdbc:postgresql://localhost:5432/github`  | JDBC URL                                                             |
| `DB_USER`      | `postgres`                                 | Database user                                                        |
| `DB_PASSWORD`  | `postgres`                                 | Database password                                                    |
| `GITHUB_TOKEN` | *(none)*                                   | Optional. Raises GitHub's search limit from 10 to 30 requests/minute |

Other settings live in `src/main/resources/application.properties` (`github.per-page`, HTTP timeouts).

### Build a runnable jar

```bash
./mvnw clean package
java -jar target/github-repo-searcher-1.0.0.jar
```

---

## Testing

```bash
./mvnw test
```

No Docker or database is needed: tests that touch the database start an embedded, real PostgreSQL
and apply the same Flyway migrations as production. GitHub is never called; it is stubbed at the HTTP level.

| Test class                         | Layer        | Type                                              |
|------------------------------------|--------------|---------------------------------------------------|
| `SortFieldTest`, `RepositoryFilterTest` | model   | Unit                                              |
| `GithubRestClientTest`             | client       | Unit, GitHub stubbed with `MockRestServiceServer` |
| `GithubRepoServiceTest`            | service      | Unit, collaborators mocked with Mockito           |
| `GithubRepoControllerTest`         | controller   | `@WebMvcTest` slice, service mocked               |
| `JdbcGithubRepoRepositoryTest`     | repository   | `@JdbcTest` slice against real PostgreSQL         |
| `GithubRepoApiIntegrationTest`     | all          | End-to-end through HTTP, real PostgreSQL          |

76 tests. Coverage report (JaCoCo): `target/site/jacoco/index.html`, about 97% of lines.

### Postman

Import `postman/GitHub-Repository-Searcher.postman_collection.json`. It has a `baseUrl` variable
(default `http://localhost:8080`), and every request carries a status assertion, so the whole
collection can be run with the Collection Runner. Run the searches first: they fill the database
that the retrieve requests read.

---

## API

### 1. Search GitHub and store the results

`POST /api/github/search`

| Field      | Required | Rules                                       | Description                                   |
|------------|----------|---------------------------------------------|-----------------------------------------------|
| `query`    | yes      | not blank, max 200 characters               | Repository name, partial or full match        |
| `language` | no       | max 50 characters                           | Programming language, e.g. `Java`             |
| `sort`     | no       | `stars`, `forks` or `updated`               | Sort order, descending (default `stars`)      |

```bash
curl -X POST http://localhost:8080/api/github/search \
  -H 'Content-Type: application/json' \
  -d '{"query": "spring boot", "language": "Java", "sort": "stars"}'
```

```json
{
  "message": "Repositories fetched and saved successfully",
  "repositories": [
    {
      "id": 6296790,
      "name": "spring-boot",
      "fullName": "spring-projects/spring-boot",
      "description": "Spring Boot helps you to create Spring-powered, production-grade applications...",
      "owner": "spring-projects",
      "language": "Java",
      "stars": 81888,
      "forks": 42724,
      "openIssues": 533,
      "url": "https://github.com/spring-projects/spring-boot",
      "lastUpdated": "2026-11-17T07:35:19Z"
    }
  ]
}
```

- Up to `github.per-page` (default 100, GitHub's maximum) repositories are fetched per search.
- A repository that is already stored is updated, not duplicated.
- When nothing matches: `200` with `"message": "No repositories found"` and an empty list.
- If GitHub fails, nothing is written to the database.

### 2. Retrieve stored results

`GET /api/github/repositories`

| Parameter  | Required | Description                                                        |
|------------|----------|--------------------------------------------------------------------|
| `language` | no       | Filter by programming language (case-insensitive)                  |
| `minStars` | no       | Minimum stars count, inclusive, `>= 0` (default `0`)               |
| `sort`     | no       | `stars`, `forks` or `updated`, descending (default `stars`)        |

```bash
curl 'http://localhost:8080/api/github/repositories?language=Java&minStars=100&sort=stars'
```

```json
{ "repositories": [ { "id": 6296790, "name": "spring-boot", "...": "same fields as above" } ] }
```

### Errors

All errors use RFC 9457 `application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "query must not be blank; sort must be one of: stars, forks, updated",
  "instance": "/api/github/search"
}
```

| Status | When                                                                                                |
|--------|-----------------------------------------------------------------------------------------------------|
| `400`  | Invalid input: blank or too long `query`, unknown `sort`, negative or non-numeric `minStars`, bad JSON |
| `429`  | GitHub rate limit reached; GitHub's `Retry-After` header is passed through when present             |
| `502`  | GitHub is unreachable, returned an error, or returned a response that could not be parsed           |
| `500`  | Unexpected error (details are logged, never returned to the client)                                 |

---

## Architecture

```
src/main/java/com/example/githubsearcher
├── GithubRepoSearcherApplication.java   Spring Boot entry point
├── config/        GithubProperties (typed, validated settings), GithubClientConfig (RestClient), OpenApiConfig
├── controller/    GithubRepoController: HTTP endpoints, request validation
├── dto/           API contract: SearchRequest, SearchResponse, RepositoryListResponse, RepositoryResponse
├── service/       GithubRepoService: business logic (search, then save; query stored data)
├── client/        GithubClient (port) and GithubRestClient (adapter for the GitHub REST API)
│   └── dto/       GitHub's JSON model: GithubSearchResponse, GithubRepoItem, GithubOwner
├── repository/    GithubRepoRepository (port) and JdbcGithubRepoRepository (PostgreSQL adapter)
├── model/         Domain: GithubRepo, RepositoryFilter, SortField
├── mapper/        GithubRepoMapper: GitHub model -> domain -> API response
└── exception/     GithubApiException, GithubRateLimitException, GlobalExceptionHandler
src/main/resources
├── application.properties
└── db/migration/V1__create_repositories_table.sql
```

Request flow: `Controller -> Service -> GithubClient` (fetch) `-> Mapper -> Repository` (upsert) `-> Mapper -> response`.

### Design decisions

- **Layered architecture with ports and adapters.** The service depends on the `GithubClient` and
  `GithubRepoRepository` interfaces, not on HTTP or SQL details, so each layer is tested in isolation
  and implementations can be swapped (e.g. JPA, another API client).
- **Separate models per boundary.** GitHub's JSON (`client/dto`), the domain (`model`) and the public
  API (`dto`) are distinct types, so a change in GitHub's API never leaks into this API's contract.
- **Efficient upsert.** One batched `INSERT ... ON CONFLICT (id) DO UPDATE` per search, keyed on the
  GitHub repository id (primary key): no read-before-write, no duplicates.
- **Indexed filters.** `lower(language)` and `stars` are indexed for the retrieve endpoint.
- **No DB connection held during the GitHub call.** The search and the save are not one transaction;
  the upsert runs in its own short transaction after GitHub responds.
- **SQL injection safe.** Filters are bound parameters; `ORDER BY` columns come from the `SortField`
  enum whitelist.
- **Schema versioning with Flyway**, and fail-fast validation of configuration at startup.
- **Name matching** uses GitHub's `in:name` qualifier, so `query` matches repository names partially or fully.
