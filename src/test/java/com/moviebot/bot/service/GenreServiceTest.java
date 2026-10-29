package com.moviebot.bot.service;

import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.repo.GenreRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenreServiceTest {

    private final GenreRepository repository = mock(GenreRepository.class);
    private final GenreService service = new GenreService(repository);

    @Test
    void addsSeveralCommaSeparatedGenres() {
        GenreService.AddResult result = service.addAll(" Komediya ,Drama,  Jangari  film ");

        assertThat(result.added()).containsExactly("Komediya", "Drama", "Jangari film");
        verify(repository, times(3)).save(any(Genre.class));
    }

    @Test
    void skipsExistingDuplicatesAndTooLongNames() {
        when(repository.existsByNameIgnoreCase("Drama")).thenReturn(true);

        GenreService.AddResult result = service.addAll("Drama, Tarix, tarix, " + "x".repeat(41) + ", ,");

        assertThat(result.added()).containsExactly("Tarix");
        assertThat(result.skipped()).containsExactly("Drama", "tarix", "x".repeat(41));
    }

    @Test
    void toggleRemovesWhenAlreadyLinked() {
        when(repository.unlink(1L, 2L)).thenReturn(1);

        assertThat(service.toggle(1L, 2L)).isFalse();
        verify(repository, never()).link(any(), any());
    }

    @Test
    void toggleAddsWhenNotLinked() {
        when(repository.unlink(1L, 2L)).thenReturn(0);

        assertThat(service.toggle(1L, 2L)).isTrue();
        verify(repository).link(1L, 2L);
    }

    @Test
    void usersSeeOnlyGenresWithMovies() {
        GenreRepository.GenreWithCount empty = count(1L, "Bo'sh", 0L);
        GenreRepository.GenreWithCount full = count(2L, "Drama", 3L);
        when(repository.findAllWithMovieCount()).thenReturn(List.of(empty, full));

        assertThat(service.findNonEmpty()).containsExactly(full);
    }

    private static GenreRepository.GenreWithCount count(Long id, String name, Long movies) {
        return new GenreRepository.GenreWithCount() {
            public Long getId() { return id; }
            public String getName() { return name; }
            public Long getMovies() { return movies; }
        };
    }
}
