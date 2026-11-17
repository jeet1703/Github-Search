package com.example.githubsearcher.exception;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GithubRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(GithubRateLimitException ex) {
        log.warn("GitHub rate limit exceeded (retry-after: {})", ex.getRetryAfter());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("GitHub rate limit exceeded");
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (ex.getRetryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER, ex.getRetryAfter());
        }
        return response.body(problem);
    }

    @ExceptionHandler(GithubApiException.class)
    public ResponseEntity<ProblemDetail> handleGithubError(GithubApiException ex) {
        log.error("GitHub API call failed: {}", ex.getMessage(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        problem.setTitle("GitHub API error");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred"));
    }

    /** Bad JSON body. By default Spring just says "Invalid request content"; this lists what's actually wrong. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setDetail(ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage).sorted().collect(Collectors.joining("; ")));
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }

    /** Same thing, for bad query parameters like minStars=-1. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ex.getBody().setDetail(ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage).sorted().collect(Collectors.joining("; ")));
        return super.handleHandlerMethodValidationException(ex, headers, status, request);
    }
}
