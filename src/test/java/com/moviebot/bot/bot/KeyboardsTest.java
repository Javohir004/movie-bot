package com.moviebot.bot.bot;

import com.moviebot.bot.domain.Movie;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeyboardsTest {

    // tarjima o'rniga kalitning o'zini qaytaradi
    private static final java.util.function.Function<String, String> TR = key -> key;

    private static List<Movie> movies(int count) {
        List<Movie> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Movie movie = new Movie();
            movie.setCode("A" + i);
            movie.setTitle("Kino " + i);
            list.add(movie);
        }
        return list;
    }

    private static List<String> callbacks(InlineKeyboardMarkup markup) {
        return markup.getKeyboard().stream()
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getCallbackData)
                .toList();
    }

    @Test
    void smallListHasNoNavigation() {
        List<String> data = callbacks(Keyboards.userMovieListPage(movies(7), 0, TR));

        assertThat(data).hasSize(8).doesNotContain("USERLIST:1");
    }

    @Test
    void firstPageShowsTenItemsAndNextButton() {
        List<String> data = callbacks(Keyboards.userMovieListPage(movies(25), 0, TR));

        assertThat(data).contains("MOVIE:A1", "MOVIE:A10", "USERLIST:1").doesNotContain("MOVIE:A11", "USERLIST:-1");
    }

    @Test
    void middlePageHasBothDirections() {
        List<String> data = callbacks(Keyboards.userMovieListPage(movies(25), 1, TR));

        assertThat(data).contains("MOVIE:A11", "MOVIE:A20", "USERLIST:0", "USERLIST:2");
    }

    @Test
    void pageOutOfRangeIsClampedToLastPage() {
        List<String> data = callbacks(Keyboards.userMovieListPage(movies(25), 99, TR));

        assertThat(data).contains("MOVIE:A21", "MOVIE:A25", "USERLIST:1").doesNotContain("USERLIST:3");
    }

    @Test
    void hugeListStaysSmall() {
        InlineKeyboardMarkup markup = Keyboards.movieListMenu(movies(500), 7);
        long buttons = markup.getKeyboard().stream().mapToLong(List::size).sum();

        assertThat(buttons).isLessThanOrEqualTo(Keyboards.PAGE_SIZE + 4);
        assertThat(callbacks(markup)).contains("LISTITEM:A71", "ADMINLIST:6", "ADMINLIST:8");
    }

    @Test
    void genreToggleMarksSelectedGenres() {
        List<com.moviebot.bot.domain.Genre> genres = List.of(
                com.moviebot.bot.domain.Genre.builder().id(1L).name("Drama").build(),
                com.moviebot.bot.domain.Genre.builder().id(2L).name("Komediya").build());

        InlineKeyboardMarkup markup = Keyboards.movieGenreToggleMenu(10L, "A1", genres, java.util.Set.of(2L));
        List<String> texts = markup.getKeyboard().stream().flatMap(List::stream).map(InlineKeyboardButton::getText).toList();

        assertThat(texts).contains("Drama", "✅ Komediya");
        assertThat(callbacks(markup)).contains("MGT:10:1", "MGT:10:2", "LISTITEM:A1");
    }

    @Test
    void genreMoviePagesKeepGenreInCallback() {
        List<String> data = callbacks(Keyboards.userGenreMoviePage(movies(25), 7L, 1, TR));

        assertThat(data).contains("UGP:7:0", "UGP:7:2", "USER:GENRES");
    }

    @Test
    void longestCallbackFitsTelegramLimitForMaxCodeLength() {
        String maxCode = "X".repeat(20);
        Movie movie = new Movie();
        movie.setCode(maxCode);
        movie.setTitle("t");
        movie.setType(com.moviebot.bot.enums.MovieType.SERIES);

        for (String data : callbacks(Keyboards.movieDetailMenu(movie))) {
            assertThat(data.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
        }
    }
}
