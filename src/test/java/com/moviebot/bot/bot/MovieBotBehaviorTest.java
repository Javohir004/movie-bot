package com.moviebot.bot.bot;

import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.service.MovieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;

import java.util.List;
import java.util.Optional;

import static com.moviebot.bot.bot.BotHarness.ADMIN_ID;
import static com.moviebot.bot.bot.BotHarness.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Botning tashqi xatti-harakati: qanday update'ga qanday javob yuboradi.
// MovieBot ichki tuzilishi o'zgarganda (refaktoring) bu testlar o'zgarmasdan o'tishi kerak.
class MovieBotBehaviorTest {

    private BotHarness h;

    @BeforeEach
    void setUp() throws Exception {
        h = new BotHarness();
    }

    private static Movie movie(String code, String title, MovieType type) {
        return Movie.builder().id(10L).code(code).title(title).type(type).description("Tavsif").posterFileId("poster").build();
    }

    // ---- foydalanuvchi ----

    @Test
    void newUserIsAskedForLanguageFirst() {
        h.text(USER_ID, "/start");

        assertThat(h.buttons()).contains("LANG:uz", "LANG:ru", "LANG:en");
        assertThat(h.buttons()).doesNotContain("USER:SEARCH");
    }

    @Test
    void choosingLanguageShowsMenuInThatLanguage() {
        h.text(USER_ID, "/start");
        h.callback(USER_ID, "LANG:ru");

        assertThat(h.texts()).anyMatch(t -> t.contains("Язык: Русский"));
        assertThat(h.texts()).anyMatch(t -> t.contains("Добро пожаловать"));
        assertThat(h.buttons()).contains("USER:SEARCH", "USER:LANGUAGE");
    }

    @Test
    void everyCallbackIsAnsweredFirst() {
        h.addUser(USER_ID, Role.USER, "uz");
        h.callback(USER_ID, "USER:MENU");

        assertThat(h.sent.get(0)).isInstanceOf(AnswerCallbackQuery.class);
    }

    @Test
    void searchByCodeShowsMovieCard() {
        h.addUser(USER_ID, Role.USER, "uz");
        Movie movie = movie("101", "Tinch okeani", MovieType.MOVIE);
        when(h.movieService.search("101")).thenReturn(new MovieService.SearchResult(List.of(movie), List.of()));
        when(h.movieService.findByCode("101")).thenReturn(Optional.of(movie));

        h.text(USER_ID, "101");

        SendPhoto card = h.sentOf(SendPhoto.class).get(0);
        assertThat(card.getCaption()).contains("Tinch okeani", "Kodi: 101");
        assertThat(h.buttons()).contains("WATCHMOVIE:101", "USER:MENU");
    }

    @Test
    void unknownCommandForUser() {
        h.addUser(USER_ID, Role.USER, "uz");

        h.text(USER_ID, "/foo");

        assertThat(h.lastText()).isEqualTo("Bunday buyruq mavjud emas.");
    }

    @Test
    void unsubscribedUserGetsChannelPromptInsteadOfContent() {
        h.addUser(USER_ID, Role.USER, "uz");
        RequiredChannel channel = RequiredChannel.builder().id(5L).channelUsername("kanal").build();
        when(h.channelService.getActiveChannels()).thenReturn(List.of(channel));
        h.memberStatus.put("@kanal", "left");

        h.callback(USER_ID, "MOVIE:101");

        assertThat(h.buttons()).contains("https://t.me/kanal", "CHECKSUB");
        verify(h.movieService, never()).findByCode(any());
    }

    @Test
    void regularUserCannotUseAdminButtons() {
        h.addUser(USER_ID, Role.USER, "uz");

        h.callback(USER_ID, "ADMIN:STATS");

        assertThat(h.sent).hasSize(1).first().isInstanceOf(AnswerCallbackQuery.class);
        verifyNoInteractions(h.statsService);
    }

    @Test
    void searchSuggestsSimilarTitles() {
        h.addUser(USER_ID, Role.USER, "uz");
        when(h.movieService.search("narto")).thenReturn(new MovieService.SearchResult(List.of(),
                List.of(movie("N1", "Naruto", MovieType.ANIME))));

        h.text(USER_ID, "narto");

        assertThat(h.lastText()).contains("Balki");
        assertThat(h.buttons()).containsExactly("MOVIE:N1");
    }

    @Test
    void seasonShowsEpisodeGridAndEpisodeShowsVideoWithNext() {
        h.addUser(USER_ID, Role.USER, "ru");
        Movie anime = movie("N1", "Naruto", MovieType.ANIME);
        com.moviebot.bot.domain.Season season = com.moviebot.bot.domain.Season.builder().id(3L).movie(anime).seasonNumber(1).build();
        com.moviebot.bot.domain.Episode ep1 = com.moviebot.bot.domain.Episode.builder().id(5L).season(season).episodeNumber(1).fileId("v1").build();
        com.moviebot.bot.domain.Episode ep2 = com.moviebot.bot.domain.Episode.builder().id(6L).season(season).episodeNumber(2).fileId("v2").build();
        when(h.seasonService.findById(3L)).thenReturn(Optional.of(season));
        when(h.seasonService.countSeasonsForMovie(10L)).thenReturn(2);
        when(h.episodeService.getEpisodesForSeason(3L)).thenReturn(List.of(ep1, ep2));
        when(h.episodeService.getById(5L)).thenReturn(ep1);
        when(h.episodeService.findBySeasonAndEpisodeNumber(3L, 2)).thenReturn(Optional.of(ep2));

        h.callback(USER_ID, "SEASON:3");
        assertThat(h.buttons()).contains("EPISODE:5", "EPISODE:6", "SERIES:N1");
        assertThat(h.lastText()).contains("1 сезон");

        h.callback(USER_ID, "EPISODE:5");
        assertThat(h.lastText()).contains("Naruto", "1 сезон, 1 серия");
        assertThat(h.buttons()).contains("EPISODE:6", "SEASON:3");
    }

    @Test
    void userGenresUseUserLanguage() {
        h.addUser(USER_ID, Role.USER, "ru");
        var action = mock(com.moviebot.bot.repo.GenreRepository.GenreWithCount.class);
        when(action.getId()).thenReturn(1L);
        when(action.getMovies()).thenReturn(3L);
        when(action.nameFor("ru")).thenReturn("Боевик");
        when(h.genreService.findNonEmpty()).thenReturn(List.of(action));

        h.callback(USER_ID, "USER:GENRES");

        assertThat(h.buttons()).contains("GENRE:1", "USER:MENU");
        assertThat(h.lastText()).isEqualTo("🎭 Выберите жанр:");
    }

    // ---- admin ----

    @Test
    void scheduledBroadcastIsSavedForLater() {
        when(h.broadcastService.schedule(any(), any(), any(), any(), any(), any()))
                .thenReturn(Broadcast.builder().id(8L).build());

        h.callback(ADMIN_ID, "ADMIN:BROADCAST_NEW");
        h.text(ADMIN_ID, "Reklama matni");
        h.callback(ADMIN_ID, "BCAST:NOBTN");
        h.callback(ADMIN_ID, "BROADCAST:SCHEDULE");
        assertThat(h.lastText()).startsWith("Yuborish vaqtini yozing");

        h.text(ADMIN_ID, "ertaga");
        assertThat(h.lastText()).startsWith("Vaqtni tushunmadim");

        h.text(ADMIN_ID, "23:59");
        verify(h.broadcastService).schedule(eq(ADMIN_ID), eq(ADMIN_ID), any(), isNull(), isNull(),
                argThat(at -> at.isAfter(java.time.LocalDateTime.now())));
        assertThat(h.texts()).anyMatch(t -> t.contains("#8") && t.contains("da yuboriladi"));
    }

    @Test
    void removingChannelAsksConfirmationAndShowsFinalCount() {
        RequiredChannel channel = RequiredChannel.builder().id(5L).channelUsername("kanal").title("Kanal").build();
        when(h.channelService.findById(5L)).thenReturn(Optional.of(channel));
        when(h.channelService.joinCount(5L)).thenReturn(42L);

        h.callback(ADMIN_ID, "REMOVECHANNEL:5");
        assertThat(h.buttons()).contains("RMCHCONF:5");
        verify(h.channelService, never()).remove(any());

        h.callback(ADMIN_ID, "RMCHCONF:5");
        verify(h.channelService).remove(5L);
        assertThat(h.texts()).anyMatch(t -> t.contains("Kanal olib tashlandi") && t.contains("42"));
    }

    @Test
    void adminStartShowsAdminPanel() {
        h.text(ADMIN_ID, "/start");

        assertThat(h.texts()).anyMatch(t -> t.startsWith("Salom"));
        assertThat(h.buttons()).contains("ADMIN:ADD_MOVIE", "ADMIN:BROADCAST", "ADMIN:STATS");
    }

    @Test
    void addMovieFlowValidatesCode() {
        when(h.movieService.isCodeTaken("N1")).thenReturn(false);

        h.callback(ADMIN_ID, "ADMIN:ADD_MOVIE");
        assertThat(h.lastText()).startsWith("Kino/serial nomini yozing");

        h.text(ADMIN_ID, "Naruto");
        assertThat(h.lastText()).startsWith("Kod kiriting");

        h.text(ADMIN_ID, "bad code!");
        assertThat(h.lastText()).startsWith("Kod faqat lotin");

        h.text(ADMIN_ID, "N1");
        assertThat(h.buttons()).contains("TYPE:MOVIE", "TYPE:SERIES", "TYPE:ANIME");
    }

    @Test
    void addingChannelChecksUsernameAdminRightsAndAsksDuration() {
        h.callback(ADMIN_ID, "ADMIN:ADD_CHANNEL");

        h.text(ADMIN_ID, "@ab");
        assertThat(h.lastText()).startsWith("Username yoki ID noto'g'ri");

        h.text(ADMIN_ID, "@kanal_test");
        assertThat(h.lastText()).contains("bot bu kanalda admin emas");

        h.memberStatus.put("@kanal_test", "administrator");
        h.text(ADMIN_ID, "@kanal_test");
        assertThat(h.buttons()).contains("CHDUR:7", "CHDUR:0");

        when(h.channelService.add(eq("kanal_test"), isNull(), eq("Test Kanal"), isNull(), eq(false), any()))
                .thenReturn(RequiredChannel.builder().id(1L).channelUsername("kanal_test").title("Test Kanal").build());
        h.callback(ADMIN_ID, "CHDUR:7");
        verify(h.channelService).add(eq("kanal_test"), isNull(), eq("Test Kanal"), isNull(), eq(false), notNull());
        assertThat(h.texts()).anyMatch(t -> t.startsWith("✅ Test Kanal qo'shildi"));
    }

    @Test
    void broadcastFlowWithButtonPreviewAndSend() {
        when(h.broadcastService.schedule(any(), any(), any(), any(), any(), any()))
                .thenReturn(Broadcast.builder().id(7L).build());

        h.callback(ADMIN_ID, "ADMIN:BROADCAST_NEW");
        h.photo(ADMIN_ID);
        assertThat(h.buttons()).contains("BCAST:NOBTN");

        h.text(ADMIN_ID, "@void_movie_bot");
        CopyMessage preview = h.sentOf(CopyMessage.class).get(0);
        assertThat(preview.getChatId()).isEqualTo(String.valueOf(ADMIN_ID));
        assertThat(h.buttons()).contains("https://t.me/void_movie_bot", "BROADCAST:SEND", "BROADCAST:SCHEDULE");

        h.callback(ADMIN_ID, "BROADCAST:SEND");
        verify(h.broadcastService).schedule(eq(ADMIN_ID), eq(ADMIN_ID), any(), eq("➡️ @void_movie_bot"),
                eq("https://t.me/void_movie_bot"), any());
        assertThat(h.texts()).anyMatch(t -> t.contains("#7") && t.contains("navbatga"));

        // ikkinchi bosish hech narsa yubormaydi
        h.callback(ADMIN_ID, "BROADCAST:SEND");
        verify(h.broadcastService, times(1)).schedule(any(), any(), any(), any(), any(), any());
    }

    @Test
    void genreToggleUpdatesSameMessage() {
        when(h.movieService.getById(10L)).thenReturn(movie("A1", "Kino", MovieType.MOVIE));
        when(h.genreService.findAll()).thenReturn(List.of(Genre.builder().id(2L).name("Drama").build()));
        when(h.genreService.genreIdsForMovie(10L)).thenReturn(List.of(2L));

        h.callback(ADMIN_ID, "MGT:10:2");

        verify(h.genreService).toggle(10L, 2L);
        assertThat(h.sentOf(EditMessageReplyMarkup.class)).hasSize(1);
        assertThat(h.buttons()).contains("MGT:10:2", "LISTITEM:A1");
    }
}
