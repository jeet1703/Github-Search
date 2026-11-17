package com.example.githubsearcher.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "github")
public record GithubProperties(
        @NotBlank String apiUrl,
        String token,
        @Min(1) @Max(100) int perPage) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
