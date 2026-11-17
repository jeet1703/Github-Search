package com.example.githubsearcher.model;

public record RepositoryFilter(String language, int minStars, SortField sort) {

    public RepositoryFilter {
        if (minStars < 0) {
            throw new IllegalArgumentException("minStars must not be negative");
        }
        language = language == null || language.isBlank() ? null : language.trim();
        sort = sort == null ? SortField.STARS : sort;
    }
}
