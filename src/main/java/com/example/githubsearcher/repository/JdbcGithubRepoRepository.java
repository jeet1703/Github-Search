package com.example.githubsearcher.repository;

import com.example.githubsearcher.model.GithubRepo;
import com.example.githubsearcher.model.RepositoryFilter;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcGithubRepoRepository implements GithubRepoRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO repositories
                (id, name, full_name, description, owner, language, stars, forks, open_issues, url, last_updated)
            VALUES
                (:id, :name, :fullName, :description, :owner, :language, :stars, :forks, :openIssues, :url, :lastUpdated)
            ON CONFLICT (id) DO UPDATE SET
                name         = EXCLUDED.name,
                full_name    = EXCLUDED.full_name,
                description  = EXCLUDED.description,
                owner        = EXCLUDED.owner,
                language     = EXCLUDED.language,
                stars        = EXCLUDED.stars,
                forks        = EXCLUDED.forks,
                open_issues  = EXCLUDED.open_issues,
                url          = EXCLUDED.url,
                last_updated = EXCLUDED.last_updated
            """;

    // ORDER BY can't take a bind parameter, so the column name is pasted in. It always comes from
    // the SortField enum, never straight from the request, so there's no SQL injection risk.
    private static final String FIND_SQL = """
            SELECT id, name, full_name, description, owner, language, stars, forks, open_issues, url, last_updated
            FROM repositories
            WHERE (CAST(:language AS TEXT) IS NULL OR lower(language) = lower(:language))
              AND stars >= :minStars
            ORDER BY %s DESC NULLS LAST, id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final JdbcClient jdbcClient;

    public JdbcGithubRepoRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbcClient = JdbcClient.create(jdbcTemplate);
    }

    @Override
    @Transactional
    public void upsertAll(List<GithubRepo> repos) {
        if (repos.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = repos.stream()
                .map(SimplePropertySqlParameterSource::new)
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GithubRepo> findAll(RepositoryFilter filter) {
        return jdbcClient.sql(FIND_SQL.formatted(filter.sort().column()))
                .param("language", filter.language())
                .param("minStars", filter.minStars())
                .query(GithubRepo.class)
                .list();
    }
}
