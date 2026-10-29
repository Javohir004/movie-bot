package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.AdminSessionManager;
import com.moviebot.bot.bot.Keyboards;
import com.moviebot.bot.bot.TelegramSender;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.service.EpisodeService;
import com.moviebot.bot.service.GenreService;
import com.moviebot.bot.service.MovieService;
import com.moviebot.bot.service.SeasonService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Admin: kino/serial qo'shish va tahrirlash, ro'yxat, o'chirish, fasl va qismlarni boshqarish
@Component
public class AdminMovieHandler {

    // callback data 64 bayt bilan cheklangan ("ADMIN_SEASONS:" + kod ham shunga sig'ishi kerak)
    private static final int MAX_CODE_LENGTH = 20;
    private static final String CODE_PATTERN = "[A-Za-z0-9_-]{1," + MAX_CODE_LENGTH + "}";

    private final TelegramSender sender;
    private final AdminSessionManager sessionManager;
    private final MovieService movieService;
    private final SeasonService seasonService;
    private final EpisodeService episodeService;
    private final GenreService genreService;
    private final AdminPanelHandler panel;

    public AdminMovieHandler(TelegramSender sender,
                             AdminSessionManager sessionManager,
                             MovieService movieService,
                             SeasonService seasonService,
                             EpisodeService episodeService,
                             GenreService genreService,
                             AdminPanelHandler panel) {
        this.sender = sender;
        this.sessionManager = sessionManager;
        this.movieService = movieService;
        this.seasonService = seasonService;
        this.episodeService = episodeService;
        this.genreService = genreService;
        this.panel = panel;
    }

    public boolean handleCallback(long chatId, long userId, Integer messageId, String data) {
        if (data.equals("BACK")) {
            goBack(chatId, userId);
            return true;
        }

        if (data.startsWith("TYPE:") && sessionManager.getState(userId) == AdminState.AWAITING_TYPE) {
            MovieType type = MovieType.valueOf(data.substring("TYPE:".length()));
            sessionManager.getPendingMovie(userId).setType(type);
            askDescription(chatId, userId);
            return true;
        }

        if (data.equals("ADMIN:ADD_MOVIE")) {
            sessionManager.clear(userId);
            askTitle(chatId, userId);
            return true;
        }

        if (data.equals("ADMIN:LIST_MOVIES")) {
            sendMovieList(chatId);
            return true;
        }

        if (data.startsWith("ADMINLIST:")) {
            int page = Integer.parseInt(data.substring("ADMINLIST:".length()));
            sendMovieList(chatId, page, messageId);
            return true;
        }

        if (data.startsWith("LISTITEM:")) {
            showMovieDetail(chatId, data.substring("LISTITEM:".length()));
            return true;
        }

        if (data.startsWith("ADDSEASON:")) {
            startAddSeasonFlow(chatId, userId, data.substring("ADDSEASON:".length()));
            return true;
        }

        if (data.startsWith("ADMIN_SEASONS:")) {
            startManageSeasonsFlow(chatId, data.substring("ADMIN_SEASONS:".length()));
            return true;
        }

        if (data.startsWith("SEASONMANAGE:")) {
            sendSeasonManageMenu(chatId, Long.parseLong(data.substring("SEASONMANAGE:".length())));
            return true;
        }

        if (data.startsWith("SEASONPOSTER:")) {
            startEditSeasonPosterFlow(chatId, userId, Long.parseLong(data.substring("SEASONPOSTER:".length())));
            return true;
        }

        if (data.startsWith("ADDEPISODE:")) {
            startAddSingleEpisodeFlow(chatId, userId, Long.parseLong(data.substring("ADDEPISODE:".length())));
            return true;
        }

        if (data.startsWith("EDIT:")) {
            startEditMovieFlow(chatId, userId, data.substring("EDIT:".length()));
            return true;
        }

        if (data.startsWith("DELETE:")) {
            sender.sendText(chatId, "Rostdan ham o'chirmoqchimisiz?", Keyboards.deleteConfirmMenu(data.substring("DELETE:".length())));
            return true;
        }

        if (data.startsWith("DELCONF:")) {
            movieService.deleteByCode(data.substring("DELCONF:".length()));
            sender.sendText(chatId, "🗑 O'chirildi.");
            sendMovieList(chatId);
            return true;
        }

        return false;
    }

    // Kino/serial qo'shish jarayonidagi matnli javoblar; bu handler'ga tegishli holat bo'lmasa false
    public boolean handleText(long chatId, long userId, AdminState state, String text) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        switch (state) {
            case AWAITING_TITLE -> {
                pending.setTitle(text.trim());
                askCode(chatId, userId);
            }
            case AWAITING_CODE -> {
                String code = text.trim();
                boolean keepsOwnCode = pending.isEditing() && code.equals(pending.getEditingCode());

                if (!keepsOwnCode && !code.matches(CODE_PATTERN)) {
                    sender.sendText(chatId, "Kod faqat lotin harflari, raqamlar, \"-\" va \"_\" dan iborat bo'lishi va "
                            + MAX_CODE_LENGTH + " belgidan oshmasligi kerak. Qaytadan kiriting:");
                    return true;
                }

                // tahrirlashda o'z kodining faqat harf kattaligini o'zgartirish mumkin (A1 -> a1)
                boolean ownCodeAnyCase = pending.isEditing() && code.equalsIgnoreCase(pending.getEditingCode());
                if (!ownCodeAnyCase && movieService.isCodeTaken(code)) {
                    sender.sendText(chatId, "Bu kod allaqachon band. Boshqa kod kiriting:");
                    return true;
                }

                pending.setCode(code);
                askType(chatId, userId);
            }
            case AWAITING_DESCRIPTION -> {
                pending.setDescription(text.trim());
                askPoster(chatId, userId);
            }
            case AWAITING_POSTER -> {
                if (pending.isEditing() && text.trim().equalsIgnoreCase("/skip")) {
                    pending.setPosterFileId(null);
                    proceedAfterPoster(chatId, userId);
                } else {
                    sender.sendText(chatId, "Iltimos, rasm (poster) yuboring" + (pending.isEditing() ? " yoki /skip yozing." : "."));
                }
            }
            case AWAITING_VIDEO -> {
                if (pending.isEditing() && text.trim().equalsIgnoreCase("/skip")) {
                    finishAddMovie(chatId, userId, null);
                } else {
                    sender.sendText(chatId, "Iltimos, video yuboring" + (pending.isEditing() ? " yoki /skip yozing." : "."));
                }
            }
            case AWAITING_SEASON_COUNT -> {
                try {
                    int count = Integer.parseInt(text.trim());
                    if (count < 1) throw new NumberFormatException();

                    pending.setSeasonCount(count);
                    pending.setSeasonsProcessed(0);
                    askSeasonPoster(chatId, userId);
                } catch (NumberFormatException e) {
                    sender.sendText(chatId, "Iltimos, musbat butun son kiriting (masalan 3):");
                }
            }
            case AWAITING_SEASON_POSTER -> sender.sendText(chatId, "Iltimos, rasm (poster) yuboring.");
            case AWAITING_EPISODE_COUNT -> {
                try {
                    int count = Integer.parseInt(text.trim());
                    if (count < 1) throw new NumberFormatException();

                    Movie movie = movieService.getById(pending.getMovieId());
                    Season season = seasonService.create(movie, pending.getCurrentSeasonNumber(), pending.getSeasonPosterFileId());

                    pending.setEpisodeCountForCurrentSeason(count);
                    pending.setCurrentSeasonId(season.getId());
                    pending.setCurrentEpisodeNumber(1);
                    askEpisodeVideo(chatId, userId);
                } catch (NumberFormatException e) {
                    sender.sendText(chatId, "Iltimos, musbat butun son kiriting (masalan 12):");
                }
            }
            case AWAITING_EPISODE_VIDEO -> sender.sendText(chatId, "Iltimos, video yuboring.");
            default -> {
                return false;
            }
        }
        return true;
    }

    public void handlePhoto(long chatId, long userId, AdminState state, String fileId) {
        if (state == AdminState.AWAITING_POSTER) {
            PendingMovie pending = sessionManager.getPendingMovie(userId);
            pending.setPosterFileId(fileId);
            proceedAfterPoster(chatId, userId);
        } else if (state == AdminState.AWAITING_SEASON_POSTER) {
            PendingMovie pending = sessionManager.getPendingMovie(userId);
            pending.setSeasonPosterFileId(fileId);
            askEpisodeCount(chatId, userId);
        } else if (state == AdminState.AWAITING_SEASON_POSTER_EDIT) {
            handleSeasonPosterEdit(chatId, userId, fileId);
        }
    }

    public void handleVideo(long chatId, long userId, AdminState state, String fileId) {
        if (state == AdminState.AWAITING_VIDEO) {
            finishAddMovie(chatId, userId, fileId);
        } else if (state == AdminState.AWAITING_EPISODE_VIDEO) {
            handleEpisodeVideo(chatId, userId, fileId);
        } else if (state == AdminState.AWAITING_NEW_EPISODE_VIDEO) {
            handleNewEpisodeVideo(chatId, userId, fileId);
        }
    }

    // ---- "Orqaga" ----

    private void goBack(long chatId, long userId) {
        AdminState state = sessionManager.getState(userId);

        switch (state) {
            case AWAITING_CODE -> askTitle(chatId, userId);
            case AWAITING_TYPE -> askCode(chatId, userId);
            case AWAITING_DESCRIPTION -> askType(chatId, userId);
            case AWAITING_POSTER -> askDescription(chatId, userId);
            case AWAITING_VIDEO -> askPoster(chatId, userId);
            case AWAITING_SEASON_COUNT, AWAITING_SEASON_POSTER, AWAITING_EPISODE_COUNT, AWAITING_EPISODE_VIDEO ->
                    cancelSeriesCreation(chatId, userId);
            default -> {
                sessionManager.clear(userId);
                sender.sendText(chatId, "Bekor qilindi.");
                panel.showMenu(chatId);
            }
        }
    }

    private void cancelSeriesCreation(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String message = "Bekor qilindi.";

        // hali birorta qism yuklanmagan fasl ochilib qolgan bo'lsa, uni olib tashlaymiz
        if (pending.getCurrentSeasonId() != null
                && episodeService.getEpisodesForSeason(pending.getCurrentSeasonId()).isEmpty()) {
            seasonService.findById(pending.getCurrentSeasonId()).ifPresent(seasonService::delete);
        }

        if (pending.getMovieId() != null && !pending.isSeasonAdditionOnly()) {
            // yuklangan fasllar bo'lsa, admin mehnati yo'qolmasin: serialni o'chirmaymiz
            if (seasonService.countSeasonsForMovie(pending.getMovieId()) == 0) {
                movieService.deleteByCode(pending.getCode());
            } else {
                message = "Bekor qilindi. Shu paytgacha yuklangan fasl va qismlar saqlab qolindi.";
            }
        }

        sessionManager.clear(userId);
        sender.sendText(chatId, message);
        panel.showMenu(chatId);
    }

    // ---- kino/serial qo'shish va tahrirlash ----

    private void startEditMovieFlow(long chatId, long userId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        sessionManager.clear(userId);

        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setEditingCode(movie.getCode());
        pending.setTitle(movie.getTitle());
        pending.setCode(movie.getCode());
        pending.setType(movie.getType());
        pending.setDescription(movie.getDescription());

        askTitle(chatId, userId);
    }

    private void proceedAfterPoster(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.getType() == MovieType.MOVIE) {
            askVideo(chatId, userId);
        } else if (pending.isEditing()) {
            finishAddMovie(chatId, userId, null);
        } else {
            Movie movie = movieService.saveWithoutVideo(
                    pending.getTitle(), pending.getCode(), pending.getType(), pending.getDescription());

            if (pending.getPosterFileId() != null) {
                movieService.updatePoster(movie, pending.getPosterFileId());
            }

            pending.setMovieId(movie.getId());
            pending.setCurrentSeasonNumber(1);
            askSeasonCount(chatId, userId);
        }
    }

    private void finishAddMovie(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        Movie movie;
        if (pending.isEditing()) {
            movie = movieService.update(
                    pending.getEditingCode(),
                    pending.getTitle(),
                    pending.getCode(),
                    pending.getType(),
                    pending.getDescription(),
                    fileId,
                    pending.getPosterFileId()
            );
            sender.sendText(chatId, "✅ \"" + pending.getTitle() + "\" yangilandi!");
        } else {
            movie = movieService.save(
                    pending.getTitle(),
                    pending.getCode(),
                    pending.getType(),
                    pending.getDescription(),
                    fileId,
                    pending.getPosterFileId()
            );
            sender.sendText(chatId, "✅ \"" + pending.getTitle() + "\" muvaffaqiyatli qo'shildi!");
        }

        sessionManager.clear(userId);
        offerGenres(chatId, movie);
        panel.showMenu(chatId);
    }

    // kino qo'shilgandan keyin janr belgilashni taklif qiladi (janrlar bo'lsa)
    private void offerGenres(long chatId, Movie movie) {
        if (movie == null || genreService.findAll().isEmpty()) return;

        sender.sendText(chatId, "🎭 \"" + movie.getTitle() + "\" uchun janrlarni belgilaysizmi?",
                Keyboards.linkToGenres(movie.getId()));
    }

    // ---- fasl va qismlar ----

    private void startAddSeasonFlow(long chatId, long userId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        int existingSeasons = seasonService.countSeasonsForMovie(movie.getId());

        sessionManager.clear(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setMovieId(movie.getId());
        pending.setCode(movie.getCode());
        pending.setTitle(movie.getTitle());
        pending.setSeasonAdditionOnly(true);
        pending.setCurrentSeasonNumber(existingSeasons + 1);

        sender.sendText(chatId, "\"" + movie.getTitle() + "\" uchun nechta yangi fasl qo'shmoqchisiz?");
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_COUNT);
    }

    private void startManageSeasonsFlow(long chatId, String movieCode) {
        Optional<Movie> movieOpt = movieService.findByCode(movieCode);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        List<Season> seasons = seasonService.getSeasonsForMovie(movie.getId());

        if (seasons.isEmpty()) {
            sender.sendText(chatId, "Hali fasl qo'shilmagan.");
            return;
        }

        sender.sendText(chatId, "Faslni tanlang:", Keyboards.seasonManageListMenu(seasons, movie.getCode()));
    }

    private void startEditSeasonPosterFlow(long chatId, long userId, Long seasonId) {
        sessionManager.clear(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setCurrentSeasonId(seasonId);
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_POSTER_EDIT);

        sender.sendText(chatId, "Yangi poster (rasm) yuboring:");
    }

    private void startAddSingleEpisodeFlow(long chatId, long userId, Long seasonId) {
        Season season = seasonService.getById(seasonId);
        int existingEpisodes = episodeService.getEpisodesForSeason(seasonId).size();

        sessionManager.clear(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setCurrentSeasonId(seasonId);
        pending.setCurrentEpisodeNumber(existingEpisodes + 1);
        sessionManager.setState(userId, AdminState.AWAITING_NEW_EPISODE_VIDEO);

        sender.sendText(chatId, season.getSeasonNumber() + "-fasl, " + (existingEpisodes + 1) + "-qism uchun video yuboring:");
    }

    private void handleEpisodeVideo(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        Season season = seasonService.getById(pending.getCurrentSeasonId());
        episodeService.create(season, pending.getCurrentEpisodeNumber(), fileId);

        int nextEpisode = pending.getCurrentEpisodeNumber() + 1;

        if (nextEpisode <= pending.getEpisodeCountForCurrentSeason()) {
            pending.setCurrentEpisodeNumber(nextEpisode);
            askEpisodeVideo(chatId, userId);
            return;
        }

        int seasonsProcessed = pending.getSeasonsProcessed() + 1;

        if (seasonsProcessed < pending.getSeasonCount()) {
            pending.setSeasonsProcessed(seasonsProcessed);
            pending.setCurrentSeasonNumber(pending.getCurrentSeasonNumber() + 1);
            pending.setSeasonPosterFileId(null);
            askSeasonPoster(chatId, userId);
            return;
        }

        sender.sendText(chatId, "✅ \"" + pending.getTitle() + "\" uchun fasl/qismlar qo'shildi!");
        Long movieId = pending.getMovieId();
        sessionManager.clear(userId);
        // yangi serial bo'lsa janr taklif qilinadi; mavjud serialga fasl qo'shilganda shart emas
        if (movieId != null && !pending.isSeasonAdditionOnly()) {
            offerGenres(chatId, movieService.getById(movieId));
        }
        panel.showMenu(chatId);
    }

    private void handleSeasonPosterEdit(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        Season season = seasonService.getById(pending.getCurrentSeasonId());

        seasonService.updatePoster(season, fileId);

        sessionManager.clear(userId);
        sender.sendText(chatId, "✅ Poster yangilandi.");
        sendSeasonManageMenu(chatId, season.getId());
    }

    private void handleNewEpisodeVideo(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        Season season = seasonService.getById(pending.getCurrentSeasonId());

        episodeService.create(season, pending.getCurrentEpisodeNumber(), fileId);

        sessionManager.clear(userId);
        sender.sendText(chatId, "✅ " + pending.getCurrentEpisodeNumber() + "-qism qo'shildi!");
        sendSeasonManageMenu(chatId, season.getId());
    }

    private void sendSeasonManageMenu(long chatId, Long seasonId) {
        Season season = seasonService.getById(seasonId);
        sender.sendText(chatId, season.getSeasonNumber() + "-fasl:", Keyboards.seasonManageMenu(seasonId, season.getMovie().getCode()));
    }

    // ---- ro'yxat va kartochka ----

    private void sendMovieList(long chatId) {
        sendMovieList(chatId, 0, null);
    }

    private void sendMovieList(long chatId, int page, Integer editMessageId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sender.sendText(chatId, "Hozircha hech qanday kino qo'shilmagan.");
            return;
        }

        sender.sendOrEdit(chatId, editMessageId, "Barcha kino/seriallar:", Keyboards.movieListMenu(movies, page));
    }

    private void showMovieDetail(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sender.sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        String info = movie.getTitle() + "\n"
                + "Kod: " + movie.getCode() + "\n"
                + "Turi: " + movie.getType() + "\n\n"
                + movie.getDescription();

        sender.sendText(chatId, info, Keyboards.movieDetailMenu(movie));
    }

    // ---- savollar ----

    private void askTitle(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_TITLE);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy nom: " + pending.getTitle() : "";

        sender.sendText(chatId, "Kino/serial nomini yozing:" + current, Keyboards.backOnly());
    }

    private void askCode(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_CODE);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy kod: " + pending.getCode() : "";

        sender.sendText(chatId, "Kod kiriting (masalan A103):" + current, Keyboards.backOnly());
    }

    private void askType(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_TYPE);
        sender.sendText(chatId, "Turi qanday?", Keyboards.movieTypeMenu());
    }

    private void askDescription(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_DESCRIPTION);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy tavsif: " + pending.getDescription() : "";

        sender.sendText(chatId, "Tavsifini yozing:" + current, Keyboards.backOnly());
    }

    private void askPoster(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_POSTER);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String note = pending.isEditing() ? "\n\nEski posterni saqlab qolish uchun /skip yozing." : "";

        sender.sendText(chatId, "Endi poster (rasm) yuboring:" + note, Keyboards.backOnly());
    }

    private void askVideo(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_VIDEO);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String note = pending.isEditing() ? "\n\nVideoni yangilamoqchi bo'lmasangiz /skip yozing." : "";

        sender.sendText(chatId, "Endi videoni yuboring:" + note, Keyboards.backOnly());
    }

    private void askSeasonCount(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_COUNT);
        sender.sendText(chatId, "Nechta fasl bo'ladi?");
    }

    private void askSeasonPoster(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_POSTER);
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        sender.sendText(chatId, pending.getCurrentSeasonNumber() + "-fasl uchun poster (rasm) yuboring:", Keyboards.backOnly());
    }

    private void askEpisodeCount(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_EPISODE_COUNT);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        sender.sendText(chatId, pending.getCurrentSeasonNumber() + "-fasl uchun nechta qism bo'ladi?");
    }

    private void askEpisodeVideo(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_EPISODE_VIDEO);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        sender.sendText(chatId, pending.getCurrentSeasonNumber() + "-fasl, " + pending.getCurrentEpisodeNumber() + "-qism uchun video yuboring:");
    }
}
