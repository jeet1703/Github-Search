package com.example.githubsearcher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SortFieldTest {

    @ParameterizedTest
    @CsvSource({"stars, STARS", "forks, FORKS", "updated, UPDATED", "' Stars ', STARS", "UPDATED, UPDATED"})
    void parsesClientValuesCaseInsensitively(String value, SortField expected) {
        assertThat(SortField.fromValue(value)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void defaultsToStars(String value) {
        assertThat(SortField.fromValue(value)).isEqualTo(SortField.STARS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "stars;drop table repositories", "star"})
    void rejectsUnknownValues(String value) {
        assertThatThrownBy(() -> SortField.fromValue(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(SortField.VALIDATION_MESSAGE);
    }

    @Test
    void mapsToWhitelistedColumns() {
        assertThat(SortField.STARS.column()).isEqualTo("stars");
        assertThat(SortField.FORKS.column()).isEqualTo("forks");
        assertThat(SortField.UPDATED.column()).isEqualTo("last_updated");
    }

    @Test
    void validationPatternMatchesEveryValue() {
        for (SortField sort : SortField.values()) {
            assertThat(sort.value()).matches(SortField.PATTERN);
        }
    }
}
