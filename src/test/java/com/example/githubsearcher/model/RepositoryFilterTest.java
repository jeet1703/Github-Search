package com.example.githubsearcher.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryFilterTest {

    @Test
    void normalizesLanguageAndDefaultsSort() {
        RepositoryFilter filter = new RepositoryFilter("  Java ", 10, null);
        assertThat(filter.language()).isEqualTo("Java");
        assertThat(filter.minStars()).isEqualTo(10);
        assertThat(filter.sort()).isEqualTo(SortField.STARS);
    }

    @Test
    void blankLanguageMeansAnyLanguage() {
        assertThat(new RepositoryFilter("   ", 0, SortField.FORKS).language()).isNull();
        assertThat(new RepositoryFilter(null, 0, SortField.FORKS).language()).isNull();
    }

    @Test
    void rejectsNegativeMinStars() {
        assertThatThrownBy(() -> new RepositoryFilter(null, -1, SortField.STARS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("minStars must not be negative");
    }
}
