package com.example.githubsearcher.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GithubProperties.class)
public class GithubClientConfig {

    static final String GITHUB_JSON = "application/vnd.github+json";
    static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    static final String API_VERSION = "2022-11-28";

    @Bean
    public RestClient githubHttpClient(RestClient.Builder builder, GithubProperties properties) {
        builder.baseUrl(properties.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, GITHUB_JSON)
                .defaultHeader(API_VERSION_HEADER, API_VERSION);
        if (properties.hasToken()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        return builder.build();
    }
}
