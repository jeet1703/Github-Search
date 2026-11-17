package com.example.githubsearcher.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Starts a real Postgres just for the tests and throws it away afterwards. No Docker needed.
 * We use real Postgres instead of H2 because the upsert uses Postgres-only SQL.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfig {

    @Bean(destroyMethod = "close")
    public EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.start();
    }

    @Bean
    public DataSource dataSource(EmbeddedPostgres postgres) {
        return postgres.getPostgresDatabase();
    }
}
