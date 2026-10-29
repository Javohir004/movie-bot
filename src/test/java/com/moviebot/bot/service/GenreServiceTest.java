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

    @Test
    void namesForMovieUseUserLanguageAndFallBackToBaseName() {
        Genre action = Genre.builder().id(1L).name("Action").nameUz("Jangari").nameRu("Боевик").build();
        Genre isekai = Genre.builder().id(2L).name("Isekai").build();
        when(repository.findByMovie(5L)).thenReturn(List.of(action, isekai));

        // alifbo tartibida: lotin harflari kirilldan oldin keladi
        assertThat(service.namesForMovie(5L, "ru")).containsExactly("Isekai", "Боевик");
        assertThat(service.namesForMovie(5L, "uz")).containsExactly("Isekai", "Jangari");
        assertThat(service.namesForMovie(5L, "en")).containsExactly("Action", "Isekai");
    }

    @Test
    void setTranslationSavesTrimmedValue() {
        Genre genre = Genre.builder().id(1L).name("Action").build();
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(genre));
        when(repository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setTranslation(1L, "ru", "  Боевик  ");

        assertThat(genre.getNameRu()).isEqualTo("Боевик");
    }

    @Test
    void dashClearsTranslation() {
        Genre genre = Genre.builder().id(1L).name("Action").nameEn("Action!").build();
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(genre));
        when(repository.save(any(Genre.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setTranslation(1L, "en", "-");

        assertThat(genre.getNameEn()).isNull();
        assertThat(genre.nameFor("en")).isEqualTo("Action");
    }

    @Test
    void tooLongTranslationIsRejected() {
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(Genre.builder().id(1L).name("A").build()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.setTranslation(1L, "uz", "x".repeat(41)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void unknownGenreReturnsEmpty() {
        when(repository.findById(9L)).thenReturn(java.util.Optional.empty());

        assertThat(service.setTranslation(9L, "uz", "Drama")).isEmpty();
    }

    @Test
    void projectionPicksTranslation() {
        GenreRepository.GenreWithCount g = count(1L, "Mecha", 2L, "Mexa", "Меха", null);

        assertThat(g.nameFor("uz")).isEqualTo("Mexa");
        assertThat(g.nameFor("ru")).isEqualTo("Меха");
        assertThat(g.nameFor("en")).isEqualTo("Mecha");
        assertThat(g.nameFor(null)).isEqualTo("Mecha");
    }

    private static GenreRepository.GenreWithCount count(Long id, String name, Long movies) {
        return count(id, name, movies, null, null, null);
    }

    private static GenreRepository.GenreWithCount count(Long id, String name, Long movies, String uz, String ru, String en) {
        return new GenreRepository.GenreWithCount() {
            public Long getId() { return id; }
            public String getName() { return name; }
            public String getNameUz() { return uz; }
            public String getNameRu() { return ru; }
            public String getNameEn() { return en; }
            public Long getMovies() { return movies; }
        };
    }
}
