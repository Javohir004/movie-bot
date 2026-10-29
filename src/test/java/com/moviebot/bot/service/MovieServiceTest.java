package com.moviebot.bot.service;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.repo.MovieRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    void codeCheckIgnoresSurroundingSpaces() {
        when(repository.existsByCode("A1")).thenReturn(true);

        assertThat(service.isCodeTaken("  A1 ")).isTrue();
    }
}
