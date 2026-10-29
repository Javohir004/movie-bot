package com.moviebot.bot.service;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.repo.MovieRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovieServiceTest {

    private final MovieRepository repository = mock(MovieRepository.class);
    private final MovieService service = new MovieService(repository);

    @Test
    void deleteDeactivatesMovieAndFreesItsCode() {
        Movie movie = Movie.builder().id(5L).code("A103").title("t").build();
        when(repository.findByCodeAndActiveTrue("A103")).thenReturn(Optional.of(movie));

        service.deleteByCode("A103");

        assertThat(movie.isActive()).isFalse();
        assertThat(movie.getCode()).isEqualTo("A103#deleted-5");
        verify(repository).save(movie);
    }

    @Test
    void deleteOfUnknownCodeDoesNothing() {
        when(repository.findByCodeAndActiveTrue("NONE")).thenReturn(Optional.empty());

        service.deleteByCode("NONE");

        verify(repository, never()).save(any());
    }

    @Test
    void codeCheckIgnoresSurroundingSpacesAndCase() {
        when(repository.existsByCodeIgnoreCase("a1")).thenReturn(true);

        assertThat(service.isCodeTaken("  a1 ")).isTrue();
    }

    @Test
    void normalizeUnifiesUzbekApostrophes() {
        assertThat(MovieService.normalize("  Oʻtkan   KUNLAR ")).isEqualTo("o'tkan kunlar");
        assertThat(MovieService.normalize("G‘alaba")).isEqualTo("g'alaba");
        assertThat(MovieService.normalize("O’zbek")).isEqualTo("o'zbek");
        assertThat(MovieService.normalize("O`zbek")).isEqualTo("o'zbek");
    }

    @Test
    void translateArgumentsHaveEqualLength() {
        // SQL translate() har bir belgini mos o'rindagisiga almashtiradi
        assertThat(MovieService.APOSTROPHES).hasSameSizeAs(MovieService.PLAIN);
    }

    @Test
    void searchByCodeIgnoresCase() {
        Movie movie = Movie.builder().code("A103").title("t").build();
        when(repository.findByCodeAndActiveTrue("a103")).thenReturn(Optional.empty());
        when(repository.findFirstByCodeIgnoreCaseAndActiveTrueOrderByIdAsc("a103")).thenReturn(Optional.of(movie));

        assertThat(service.search("a103").matches()).containsExactly(movie);
    }

    @Test
    void suggestionsAreUsedOnlyWhenNothingMatches() {
        Movie similar = Movie.builder().code("N1").title("Naruto").build();
        when(repository.findByCodeAndActiveTrue(any())).thenReturn(Optional.empty());
        when(repository.findFirstByCodeIgnoreCaseAndActiveTrueOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(repository.searchByNormalizedTitle(eq("narto"), any(), any(), anyInt())).thenReturn(java.util.List.of());
        when(repository.findSimilarTitles(eq("narto"), any(), any(), anyDouble(), anyInt())).thenReturn(java.util.List.of(similar));

        MovieService.SearchResult result = service.search("Narto");

        assertThat(result.matches()).isEmpty();
        assertThat(result.suggestions()).containsExactly(similar);
    }

    @Test
    void missingTrigramExtensionGivesNoSuggestionsInsteadOfError() {
        when(repository.findByCodeAndActiveTrue(any())).thenReturn(Optional.empty());
        when(repository.findFirstByCodeIgnoreCaseAndActiveTrueOrderByIdAsc(any())).thenReturn(Optional.empty());
        when(repository.searchByNormalizedTitle(any(), any(), any(), anyInt())).thenReturn(java.util.List.of());
        when(repository.findSimilarTitles(any(), any(), any(), anyDouble(), anyInt()))
                .thenThrow(new org.springframework.dao.InvalidDataAccessResourceUsageException("function similarity does not exist"));

        assertThat(service.search("Narto").suggestions()).isEmpty();
    }
}
