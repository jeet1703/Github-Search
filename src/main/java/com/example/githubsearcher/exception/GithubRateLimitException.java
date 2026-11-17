package com.example.githubsearcher.exception;

public class GithubRateLimitException extends GithubApiException {

    private final String retryAfter;

    public GithubRateLimitException(String message, String retryAfter, Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
    }

    public String getRetryAfter() {
        return retryAfter;
    }
}
