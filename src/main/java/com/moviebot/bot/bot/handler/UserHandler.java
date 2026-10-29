package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.I18n;
import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.bot.TextFormat;
import com.moviebot.bot.domain.Episode;
import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

// Foydalanuvchi ekranlari: til, menyu, qidiruv, ro'yxatlar, janrlar, kino/serial/qism ko'rish
@Component
public class UserHandler {

    // Telegram rasm/video caption'i uchun limit
    private static final int MAX_CAPTION_LENGTH = 1024;
    // keyin qo'shiladigan "Faslni tanlang:" kabi matnlar uchun zaxira
    private static final int CAPTION_RESERVE = 60;
    // messages*.properties dagi notfound.N kalitlari soni
    private static final int NOT_FOUND_VARIANTS = 5;

    private final TelegramSender sender;
    private final BotTexts texts;
    private final SubscriptionGate gate;
    private final UserService userService;
    private final MovieService movieService;
    private final SeasonService seasonService;
    private final EpisodeService episodeService;
    private final GenreService genreService;
    private final String username;
    private final String groupLink;

    public UserHandler(TelegramSender sender,
                       BotTexts texts,
                       SubscriptionGate gate,
                       UserService userService,
                       MovieService movieService,
                       SeasonService seasonService,
                       EpisodeService episodeService,
                       GenreService genreService,
                       @Value("${bot.username}") String username,
                       @Value("${bot.group-link}") String groupLink) {
        this.sender = sender;
        this.texts = texts;
        this.gate = gate;
        this.userService = userService;
        this.movieService = movieService;
        this.seasonService = seasonService;
        this.episodeService = episodeService;
        this.genreService = genreService;
        this.username = username;
        this.groupLink = groupLink;
    }

    // Foydalanuvchi tugmalari. Tartib muhim: kontent tugmalaridan oldin obuna tekshiriladi.
    // true — tugma shu yerda ishlandi; false — bu foydalanuvchi tugmasi emas (admin tugmasi bo'lishi mumkin)
    public boolean handleCallback(long chatId, long userId, Integer messageId, String data) {
        if (data.startsWith("LANG:")) {
            String language = I18n.normalize(data.substring("LANG:".length()));
            userService.setLanguage(userId, language);
            sender.sendText(chatId, texts.t(chatId, "lang.saved"));
            if (userService.isAdmin(userId) || gate.ensureSubscribed(chatId, userId)) {
                sendUserStart(chatId);
            }
            return true;
        }

        if (data.equals("USER:LANGUAGE")) {
            sendLanguageChoice(chatId);
            return true;
        }

        if (data.equals("USER:MENU")) {
            sendUserStart(chatId);
            return true;
        }

        if (data.equals("USER:SEARCH")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            sender.sendText(chatId, texts.t(chatId, "search.prompt"), Keyboards.backToUserMenu(texts.tr(chatId)));
            return true;
        }

        if (data.equals("USER:LIST_ALL")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            sendUserMovieList(chatId, 0, null);
            return true;
        }

        if (data.startsWith("USERLIST:")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            int page = Integer.parseInt(data.substring("USERLIST:".length()));
            sendUserMovieList(chatId, page, messageId);
            return true;
        }

        if (data.equals("USER:GENRES")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            sendUserGenres(chatId);
            return true;
        }

        if (data.startsWith("GENRE:")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            sendGenreMovies(chatId, Long.parseLong(data.substring("GENRE:".length())), 0, null);
            return true;
        }

        // UGP:<janrId>:<sahifa>
        if (data.startsWith("UGP:")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            String[] parts = data.split(":");
            sendGenreMovies(chatId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), messageId);
            return true;
        }

        if (data.equals("USER:TOP")) {
            if (!gate.ensureSubscribed(chatId, userId)) return true;

            sendTopViewed(chatId);
            return true;
        }

        if (data.equals("CHECKSUB")) {
            if (gate.ensureSubscribed(chatId, userId)) {
                sendUserStart(chatId);
            }
            return true;
        }

        // eski xabardagi tugmalar orqali ham obunasiz kontent ko'rib bo'lmasin
        if (SubscriptionGate.isContentCallback(data) && !gate.ensureSubscribed(chatId, userId)) return true;

        if (data.startsWith("WATCHMOVIE:")) {
            sendActualMovieVideo(chatId, data.substring("WATCHMOVIE:".length()));
            return true;
        }

        if (data.startsWith("MOVIE:")) {
            sendMovieByCode(chatId, data.substring("MOVIE:".length()));
            return true;
        }

        if (data.startsWith("SERIES:")) {
            String code = data.substring("SERIES:".length());
            movieService.findByCode(code).ifPresent(m -> sendSeasonList(chatId, m));
            return true;
        }

        if (data.startsWith("SEASON:")) {
            sendEpisodeList(chatId, Long.parseLong(data.substring("SEASON:".length())));
            return true;
        }

        if (data.startsWith("EPISODE:")) {
            sendEpisodeVideo(chatId, Long.parseLong(data.substring("EPISODE:".length())));
            return true;
        }

        return false;
    }

    // oddiy foydalanuvchining /start'i
    public void handleStart(long chatId, long userId) {
        if (userService.findLanguage(userId).isEmpty()) {
            // birinchi marta: avval til tanlanadi, obuna tekshiruvi tanlovdan keyin
            sendLanguageChoice(chatId);
        } else if (gate.ensureSubscribed(chatId, userId)) {
            sendUserStart(chatId);
        }
    }

    public void sendUnknownCommand(long chatId) {
        sender.sendText(chatId, texts.t(chatId, "command.unknown"));
    }

    public void handleSearch(long chatId, String query) {
        MovieService.SearchResult result = movieService.search(query);
        List<Movie> matches = result.matches();

        if (matches.size() == 1) {
            sendMovieByCode(chatId, matches.get(0).getCode());
        } else if (!matches.isEmpty()) {
            sendChoices(chatId, matches, matches.size() >= MovieService.MAX_RESULTS
                    ? texts.t(chatId, "search.tooMany", MovieService.MAX_RESULTS)
                    : texts.t(chatId, "search.many"));
        } else if (!result.suggestions().isEmpty()) {
            sendChoices(chatId, result.suggestions(), texts.t(chatId, "search.suggest"));
        } else {
            sendNotFound(chatId);
        }
    }

    private void sendUserStart(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), texts.t(chatId, "user.start"));
        message.setParseMode("HTML");
        message.setReplyMarkup(Keyboards.userMainMenu(texts.tr(chatId)));
        sender.send(message);
    }

    private void sendLanguageChoice(long chatId) {
        sender.sendText(chatId, "🌐 Tilni tanlang · Выберите язык · Choose a language", Keyboards.languageMenu());
    }

    private void sendNotFound(long chatId) {
        // messages*.properties dagi notfound.1 ... notfound.5 dan tasodifiy biri
        String randomMessage = texts.t(chatId, "notfound." + (1 + new Random().nextInt(NOT_FOUND_VARIANTS)));

        sender.sendText(chatId, randomMessage);
        sendUserStart(chatId);
    }

    private void sendChoices(long chatId, List<Movie> movies, String header) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Movie m : movies) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(m.getTitle());
            button.setCallbackData("MOVIE:" + m.getCode());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        sender.sendText(chatId, header, new InlineKeyboardMarkup(rows));
    }

    // ---- ro'yxatlar ----

    private void sendUserMovieList(long chatId, int page, Integer editMessageId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "list.empty"));
            return;
        }

        sender.sendOrEdit(chatId, editMessageId, texts.t(chatId, "list.all"),
                Keyboards.userMovieListPage(movies, page, texts.tr(chatId)));
    }

    private void sendTopViewed(long chatId) {
        List<Movie> movies = movieService.findAll();

        List<Movie> top = movies.stream()
                .sorted(Comparator.comparingInt(this::effectiveViewCount).reversed())
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "list.empty"));
            return;
        }

        sender.sendText(chatId, texts.t(chatId, "list.top"), Keyboards.userMovieListMenu(top, texts.tr(chatId)));
    }

    private void sendUserGenres(long chatId) {
        String language = texts.lang(chatId);
        // foydalanuvchi tilidagi nom bo'yicha alifbo tartibida
        var genres = genreService.findNonEmpty().stream()
                .sorted(Comparator.comparing(g -> g.nameFor(language), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (genres.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "genres.empty"), Keyboards.backToUserMenu(texts.tr(chatId)));
            return;
        }
        sender.sendText(chatId, texts.t(chatId, "genres.choose"), Keyboards.userGenreMenu(genres, texts.tr(chatId), language));
    }

    private void sendGenreMovies(long chatId, Long genreId, int page, Integer editMessageId) {
        Optional<Genre> genre = genreService.findById(genreId);
        List<Movie> movies = movieService.findByGenre(genreId);

        if (genre.isEmpty() || movies.isEmpty()) {
            sendUserGenres(chatId);
            return;
        }
        sender.sendOrEdit(chatId, editMessageId, "🎭 " + genre.get().nameFor(texts.lang(chatId)) + ":",
                Keyboards.userGenreMoviePage(movies, genreId, page, texts.tr(chatId)));
    }

    // ---- kino, serial, qism ----

    private void sendMovieByCode(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "movie.notFound"));
            return;
        }

        Movie movie = movieOpt.get();

        if (movie.getType() == MovieType.MOVIE) {
            sendMoviePosterCard(chatId, movie);
        } else {
            sendSeasonList(chatId, movie);
        }
    }

    private void sendMoviePosterCard(long chatId, Movie movie) {
        String caption = buildMovieCaption(movie, chatId);
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.moviePosterMenu(movie.getCode(), texts.tr(chatId)));
    }

    private void sendSeasonList(long chatId, Movie movie) {
        List<Season> seasons = seasonService.getSeasonsForMovie(movie.getId());

        if (seasons.isEmpty()) {
            String caption = buildMovieCaption(movie, chatId) + "\n\n" + texts.t(chatId, "caption.noSeasons");
            sendMovieCaptionCard(chatId, movie, caption, Keyboards.backToUserMenu(texts.tr(chatId)));
            return;
        }

        if (seasons.size() == 1) {
            sendEpisodeList(chatId, seasons.get(0).getId());
            return;
        }

        String caption = buildMovieCaption(movie, chatId) + "\n\n" + texts.t(chatId, "caption.chooseSeason");
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.seasonListMenu(seasons, texts.tr(chatId)));
    }

    private void sendMovieCaptionCard(long chatId, Movie movie, String caption, InlineKeyboardMarkup markup) {
        if (movie.getPosterFileId() == null) {
            sender.sendHtml(chatId, caption, markup);
            return;
        }

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(movie.getPosterFileId()));
        photo.setCaption(caption);
        photo.setParseMode("HTML");
        photo.setReplyMarkup(markup);
        sender.send(photo);
    }

    private void sendEpisodeList(long chatId, Long seasonId) {
        Optional<Season> seasonOpt = seasonService.findById(seasonId);

        if (seasonOpt.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "season.gone"));
            return;
        }

        Season season = seasonOpt.get();
        List<Episode> episodes = episodeService.getEpisodesForSeason(seasonId);

        seasonService.incrementViewCount(season);

        boolean singleSeason = seasonService.countSeasonsForMovie(season.getMovie().getId()) <= 1;

        String caption = buildSeasonCaption(season, chatId);
        InlineKeyboardMarkup markup = Keyboards.episodeGridMenu(episodes, season.getMovie().getCode(), singleSeason, texts.tr(chatId));

        if (season.getPosterFileId() == null) {
            sender.sendHtml(chatId, caption, markup);
            return;
        }

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(season.getPosterFileId()));
        photo.setCaption(caption);
        photo.setParseMode("HTML");
        photo.setReplyMarkup(markup);
        sender.send(photo);
    }

    private void sendEpisodeVideo(long chatId, Long episodeId) {
        Episode episode = episodeService.getById(episodeId);
        Season season = episode.getSeason();

        Optional<Episode> nextEpisode = episodeService.findBySeasonAndEpisodeNumber(
                season.getId(), episode.getEpisodeNumber() + 1);

        Long nextEpisodeId = nextEpisode.map(Episode::getId).orElse(null);

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(episode.getFileId()));
        video.setCaption(season.getMovie().getTitle() + "\n"
                + texts.t(chatId, "episode.caption", season.getSeasonNumber(), episode.getEpisodeNumber())
                + "\n\n🤖 @" + username);
        video.setReplyMarkup(Keyboards.episodeVideoMenu(season.getId(), nextEpisodeId, texts.tr(chatId)));
        sender.send(video);
    }

    private void sendActualMovieVideo(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, texts.t(chatId, "movie.notFound"));
            return;
        }

        Movie movie = movieOpt.get();

        if (movie.getFileId() == null) {
            sender.sendText(chatId, texts.t(chatId, "video.notUploaded"), Keyboards.backToUserMenu(texts.tr(chatId)));
            return;
        }

        movieService.incrementViewCount(movie);

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(movie.getFileId()));
        video.setCaption(movie.getTitle() + "\n\n🤖 @" + username);
        video.setReplyMarkup(Keyboards.backToUserMenu(texts.tr(chatId)));
        sender.send(video);
    }

    // ---- kaptsiyalar ----

    private int effectiveViewCount(Movie movie) {
        if (movie.getType() == MovieType.MOVIE) {
            return movie.getViewCount();
        }

        return seasonService.getSeasonsForMovie(movie.getId())
                .stream()
                .mapToInt(Season::getViewCount)
                .sum();
    }

    private String buildMovieCaption(Movie movie, long chatId) {
        String header = "🎬 <b>" + TextFormat.escape(movie.getTitle()) + "</b>\n" + genresLine(movie, chatId) + "\n";
        String footer = "\n\n" + texts.t(chatId, "caption.views", TextFormat.formatCount(effectiveViewCount(movie))) + "\n"
                + texts.t(chatId, "caption.code", TextFormat.escape(movie.getCode()))
                + groupLine(chatId);
        return header + "📖 " + fitDescription(movie.getDescription(), header + footer) + footer;
    }

    private String buildSeasonCaption(Season season, long chatId) {
        Movie movie = season.getMovie();
        String header = "🎬 <b>" + TextFormat.escape(movie.getTitle()) + "</b>\n"
                + texts.t(chatId, "caption.season", season.getSeasonNumber()) + "\n" + genresLine(movie, chatId) + "\n";
        String footer = "\n\n" + texts.t(chatId, "caption.views", TextFormat.formatCount(season.getViewCount())) + "\n"
                + texts.t(chatId, "caption.code", TextFormat.escape(movie.getCode()))
                + groupLine(chatId);
        return header + "📖 " + fitDescription(movie.getDescription(), header + footer) + footer;
    }

    // janr belgilanmagan bo'lsa bo'sh
    private String genresLine(Movie movie, long chatId) {
        List<String> names = genreService.namesForMovie(movie.getId(), texts.lang(chatId));
        return names.isEmpty() ? "" : "🎭 " + TextFormat.escape(String.join(", ", names)) + "\n";
    }

    // BOT_GROUP_LINK berilmagan bo'lsa qator umuman chiqmaydi
    private String groupLine(long chatId) {
        return groupLink == null || groupLink.isBlank() ? "" : "\n\n" + texts.t(chatId, "caption.group", TextFormat.escape(groupLink));
    }

    // Caption limitiga sig'adigan qilib tavsifni qisqartiradi
    private String fitDescription(String description, String rest) {
        int available = MAX_CAPTION_LENGTH - CAPTION_RESERVE - rest.length() - "📖 ".length();
        return TextFormat.fitDescription(description, available);
    }
}
