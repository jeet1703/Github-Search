package com.example.githubsearcher.model;

import java.util.Arrays;
import java.util.Locale;

public enum SortField {

    STARS("stars", "stars"),
    FORKS("forks", "forks"),
    UPDATED("updated", "last_updated");

    /** Used by the @Pattern checks on requests. If you add a sort option, add it here too. */
    public static final String PATTERN = "stars|forks|updated";
    public static final String VALIDATION_MESSAGE = "sort must be one of: stars, forks, updated";

    private final String value;
    private final String column;

    SortField(String value, String column) {
        this.value = value;
        this.column = column;
    }

    public String value() {
        return value;
    }

    public String column() {
        return column;
    }

    public static SortField fromValue(String value) {
        if (value == null || value.isBlank()) {
            return STARS;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(sort -> sort.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(VALIDATION_MESSAGE));
    }
}
