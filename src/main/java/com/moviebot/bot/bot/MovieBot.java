package com.moviebot.bot.bot;

import com.moviebot.bot.domain.Keyboards;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.service.EpisodeService;
import com.moviebot.bot.service.MovieService;
import com.moviebot.bot.service.SeasonService;
import com.moviebot.bot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class MovieBot extends TelegramLongPollingBot {

    private final String username;
    private final MovieService movieService;
    private final UserService userService;
    private final AdminSessionManager sessionManager;
    private final SeasonService seasonService;
    private final EpisodeService episodeService;


    public MovieBot(@Value("${bot.token}") String token,
                    @Value("${bot.username}") String username,
                    MovieService movieService, UserService userService, AdminSessionManager sessionManager, SeasonService seasonService, EpisodeService episodeService) {
        super(token);
        this.username = username;
        this.movieService = movieService;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.seasonService = seasonService;
        this.episodeService = episodeService;
    }

    @Override
    public String getBotUsername() {
        return username;
    }



    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasCallbackQuery()) {
            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            long userId = update.getCallbackQuery().getFrom().getId();

            if (data.equals("USER:MENU")) {
                sendUserStart(chatId);
                return;
            }

            if (data.equals("USER:SEARCH")) {
                SendMessage message = new SendMessage(String.valueOf(chatId), "Kino nomi yoki kodini yozing:");
                message.setReplyMarkup(Keyboards.backToUserMenu());

                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (data.equals("USER:LIST_ALL")) {
                sendUserMovieList(chatId);
                return;
            }

            if (data.startsWith("MOVIE:")) {
                String code = data.substring("MOVIE:".length());
                sendMovieByCode(chatId, code);
                return;
            }

            if (userService.isAdmin(userId)) {
                handleAdminCallback(chatId, userId, data);
            }

            return;
        }

        if (update.hasMessage() && update.getMessage().hasVideo()) {
            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();

            if (userService.isAdmin(userId)) {
                AdminState state = sessionManager.getState(userId);
                String fileId = update.getMessage().getVideo().getFileId();

                if (state == AdminState.AWAITING_VIDEO) {
                    finishAddMovie(chatId, userId, fileId);
                } else if (state == AdminState.AWAITING_EPISODE_VIDEO) {
                    handleEpisodeVideo(chatId, userId, fileId);
                }
            }
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            userService.registerIfAbsent(userId);

            if (text.equals("/start")) {
                if (userService.isAdmin(userId)) {
                    String firstName = update.getMessage().getFrom().getFirstName();
                    sendText(chatId, "Salom, " + firstName + "! Xush kelibsiz.");
                    sendAdminMenu(chatId);
                } else {
                    sendUserStart(chatId);
                }
                return;
            }

            if (userService.isAdmin(userId) && sessionManager.getState(userId) != AdminState.NONE) {
                handleAdminFlowInput(chatId, userId, text);
                return;
            }

            if (text.startsWith("/") && userService.isAdmin(userId)) {
                handleAdminCommand(chatId, userId, text);
                return;
            }

            if (text.startsWith("/")) {
                sendText(chatId, "Bunday buyruq mavjud emas.");
                return;
            }

            handleSearch(chatId, text);
        }
    }


    /// handle..
    private void handleAdminCallback(long chatId, long userId, String data) {

        if (data.equals("BACK")) {
            goBack(chatId, userId);
            return;
        }

        if (data.startsWith("TYPE:") && sessionManager.getState(userId) == AdminState.AWAITING_TYPE) {
            MovieType type = MovieType.valueOf(data.substring("TYPE:".length()));
            sessionManager.getPendingMovie(userId).setType(type);
            askDescription(chatId, userId);
            return;
        }

        if (data.equals("ADMIN:ADD_MOVIE")) {
            startAddMovieFlow(chatId, userId);
            return;
        }

        if (data.equals("ADMIN:ADD_ADMIN")) {
            SendMessage message = new SendMessage(String.valueOf(chatId), "Yozing: /addadmin <telegram_id>");
            message.setReplyMarkup(Keyboards.backOnly());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        if (data.equals("ADMIN:LIST_MOVIES")) {
            sendMovieList(chatId);
            return;
        }

        if (data.equals("ADMIN:MENU")) {
            sessionManager.clear(userId);
            sendAdminMenu(chatId);
            return;
        }

        if (data.startsWith("LISTITEM:")) {
            showMovieDetail(chatId, data.substring("LISTITEM:".length()));
            return;
        }

        if (data.startsWith("EDIT:")) {
            startEditMovieFlow(chatId, userId, data.substring("EDIT:".length()));
            return;
        }

        if (data.startsWith("DELETE:")) {
            confirmDelete(chatId, data.substring("DELETE:".length()));
            return;
        }

        if (data.startsWith("DELCONF:")) {
            String code = data.substring("DELCONF:".length());
            movieService.deleteByCode(code);
            sendText(chatId, "🗑 O'chirildi.");
            sendMovieList(chatId);
            return;
        }

        sendText(chatId, "Noma'lum amal.");
    }

    private void handleAdminFlowInput(long chatId, long userId, String text) {
        AdminState state = sessionManager.getState(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        switch (state) {
            case AWAITING_TITLE -> {
                pending.setTitle(text.trim());
                askCode(chatId, userId);
            }
            case AWAITING_CODE -> {
                pending.setCode(text.trim());
                askType(chatId, userId);
            }
            case AWAITING_DESCRIPTION -> {
                pending.setDescription(text.trim());

                if (pending.getType() == MovieType.MOVIE) {
                    askVideo(chatId, userId);
                } else {
                    Movie movie = movieService.saveWithoutVideo(
                            pending.getTitle(), pending.getCode(), pending.getType(), pending.getDescription());
                    pending.setMovieId(movie.getId());
                    askSeasonCount(chatId, userId);
                }
            }
            case AWAITING_VIDEO -> {
                if (pending.isEditing() && text.trim().equalsIgnoreCase("/skip")) {
                    finishAddMovie(chatId, userId, null);
                } else {
                    sendText(chatId, "Iltimos, video yuboring" + (pending.isEditing() ? " yoki /skip yozing." : "."));
                }
            }
            case AWAITING_SEASON_COUNT -> {
                try {
                    int count = Integer.parseInt(text.trim());
                    if (count < 1) throw new NumberFormatException();

                    pending.setSeasonCount(count);
                    pending.setCurrentSeasonNumber(1);
                    askEpisodeCount(chatId, userId);
                } catch (NumberFormatException e) {
                    sendText(chatId, "Iltimos, musbat butun son kiriting (masalan 3):");
                }
            }
            case AWAITING_EPISODE_COUNT -> {
                try {
                    int count = Integer.parseInt(text.trim());
                    if (count < 1) throw new NumberFormatException();

                    Movie movie = movieService.getById(pending.getMovieId());
                    Season season = seasonService.create(movie, pending.getCurrentSeasonNumber());

                    pending.setEpisodeCountForCurrentSeason(count);
                    pending.setCurrentSeasonId(season.getId());
                    pending.setCurrentEpisodeNumber(1);
                    askEpisodeVideo(chatId, userId);
                } catch (NumberFormatException e) {
                    sendText(chatId, "Iltimos, musbat butun son kiriting (masalan 12):");
                }
            }
            case AWAITING_EPISODE_VIDEO -> sendText(chatId, "Iltimos, video yuboring.");
            default -> sendText(chatId, "Nimadir xato ketdi. Qaytadan /start bosing.");
        }
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

        int nextSeason = pending.getCurrentSeasonNumber() + 1;

        if (nextSeason <= pending.getSeasonCount()) {
            pending.setCurrentSeasonNumber(nextSeason);
            askEpisodeCount(chatId, userId);
            return;
        }

        sendText(chatId, "✅ \"" + pending.getTitle() + "\" barcha fasl va qismlari bilan qo'shildi!");
        sessionManager.clear(userId);
        sendAdminMenu(chatId);
    }

    private void handleAdminCommand(long chatId, long userId, String text) {
        if (text.startsWith("/addadmin")) {
            handleAddAdmin(chatId, text);
            return;
        }

        sendText(chatId, "Noma'lum admin buyrug'i.");
    }

    private void handleAddAdmin(long chatId, String text) {
        String[] parts = text.trim().split("\\s+");

        if (parts.length != 2) {
            sendText(chatId, "Foydalanish: /addadmin <telegram_id>");
            return;
        }

        try {
            long newAdminId = Long.parseLong(parts[1]);
            userService.makeAdmin(newAdminId);
            sendText(chatId, "Foydalanuvchi (" + newAdminId + ") admin qilindi.");
        } catch (NumberFormatException e) {
            sendText(chatId, "Telegram ID raqam bo'lishi kerak.");
        }
    }

    private void handleSearch(long chatId, String query) {

        Optional<Movie> byCode = movieService.findByCode(query);

        if (byCode.isPresent()) {
            sendMovieByCode(chatId, byCode.get().getCode());
            return;
        }

        List<Movie> byTitle = movieService.findByTitle(query);

        if (byTitle.isEmpty()) {
            sendText(chatId, "Hech narsa topilmadi. Boshqa nom yoki kod bilan urinib ko'ring.");
        } else if (byTitle.size() == 1) {
            sendMovieByCode(chatId, byTitle.get(0).getCode());
        } else {
            sendChoices(chatId, byTitle);
        }
    }


    ///  start..
    private void startAddMovieFlow(long chatId, long userId) {
        sessionManager.clear(userId);
        askTitle(chatId, userId);
    }

    private void startEditMovieFlow(long chatId, long userId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
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


    /// .....
    private void goBack(long chatId, long userId) {
        AdminState state = sessionManager.getState(userId);

        switch (state) {
            case AWAITING_CODE -> askTitle(chatId, userId);
            case AWAITING_TYPE -> askCode(chatId, userId);
            case AWAITING_DESCRIPTION -> askType(chatId, userId);
            case AWAITING_VIDEO -> askDescription(chatId, userId);
            case AWAITING_SEASON_COUNT, AWAITING_EPISODE_COUNT, AWAITING_EPISODE_VIDEO -> cancelSeriesCreation(chatId, userId);
            default -> {
                sessionManager.clear(userId);
                sendText(chatId, "Bekor qilindi.");
                sendAdminMenu(chatId);
            }
        }
    }

    private void cancelSeriesCreation(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.getMovieId() != null) {
            movieService.deleteByCode(pending.getCode());
        }

        sessionManager.clear(userId);
        sendText(chatId, "Bekor qilindi, kiritilgan ma'lumotlar o'chirildi.");
        sendAdminMenu(chatId);
    }

    private void finishAddMovie(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.isEditing()) {
            movieService.update(
                    pending.getEditingCode(),
                    pending.getTitle(),
                    pending.getCode(),
                    pending.getType(),
                    pending.getDescription(),
                    fileId
            );
            sendText(chatId, "✅ \"" + pending.getTitle() + "\" yangilandi!");
        } else {
            movieService.save(
                    pending.getTitle(),
                    pending.getCode(),
                    pending.getType(),
                    pending.getDescription(),
                    fileId
            );
            sendText(chatId, "✅ \"" + pending.getTitle() + "\" muvaffaqiyatli qo'shildi!");
        }

        sessionManager.clear(userId);
        sendAdminMenu(chatId);
    }

    private void showMovieDetail(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        String info = movie.getTitle() + "\n"
                + "Kod: " + movie.getCode() + "\n"
                + "Turi: " + movie.getType() + "\n\n"
                + movie.getDescription();

        SendMessage message = new SendMessage(String.valueOf(chatId), info);
        message.setReplyMarkup(Keyboards.movieDetailMenu(movie.getCode()));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void confirmDelete(long chatId, String code) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Rostdan ham o'chirmoqchimisiz?");
        message.setReplyMarkup(Keyboards.deleteConfirmMenu(code));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    ///  send...
    private void sendAdminMenu(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Admin panel:");
        message.setReplyMarkup(Keyboards.adminMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendChoices(long chatId, List<Movie> movies) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Movie m : movies) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(m.getTitle());
            button.setCallbackData("MOVIE:" + m.getCode());

            List<InlineKeyboardButton> row = new ArrayList<>();
            row.add(button);
            rows.add(row);
        }

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = new SendMessage(String.valueOf(chatId), "Bir nechtasi topildi, birini tanlang:");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMovieByCode(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();

        if (movie.getFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId),
                    movie.getTitle() + "\n\n" + movie.getDescription() + "\n\n(Video hali yuklanmagan)");
            message.setReplyMarkup(Keyboards.backToUserMenu());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(movie.getFileId()));
        video.setCaption(movie.getTitle() + "\n\n" + movie.getDescription());
        video.setReplyMarkup(Keyboards.backToUserMenu());

        try {
            execute(video);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendMovieList(long chatId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sendText(chatId, "Hozircha hech qanday kino qo'shilmagan.");
            return;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), "Barcha kino/seriallar:");
        message.setReplyMarkup(Keyboards.movieListMenu(movies));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendUserMovieList(long chatId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sendText(chatId, "Hozircha hech qanday kino yo'q.");
            return;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), "Barcha kino/serial/animelar:");
        message.setReplyMarkup(Keyboards.userMovieListMenu(movies));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendUserStart(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId),
                "Salom! Men kino va anime botiman.\n\nKino qidirish yoki barcha kino/seriallarni ko'rish uchun pastdagi tugmalardan foydalaning.");
        message.setReplyMarkup(Keyboards.userMainMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }


    ///  ask...
    private void askTitle(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_TITLE);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy nom: " + pending.getTitle() : "";

        SendMessage message = new SendMessage(String.valueOf(chatId), "Kino/serial nomini yozing:" + current);
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askCode(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_CODE);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy kod: " + pending.getCode() : "";

        SendMessage message = new SendMessage(String.valueOf(chatId), "Kod kiriting (masalan A103):" + current);
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askType(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_TYPE);

        SendMessage message = new SendMessage(String.valueOf(chatId), "Turi qanday?");
        message.setReplyMarkup(Keyboards.movieTypeMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askDescription(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_DESCRIPTION);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String current = pending.isEditing() ? "\n\nJoriy tavsif: " + pending.getDescription() : "";

        SendMessage message = new SendMessage(String.valueOf(chatId), "Tavsifini yozing:" + current);
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askVideo(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_VIDEO);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String note = pending.isEditing() ? "\n\nVideoni yangilamoqchi bo'lmasangiz /skip yozing." : "";

        SendMessage message = new SendMessage(String.valueOf(chatId), "Endi videoni yuboring:" + note);
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askSeasonCount(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_COUNT);
        sendText(chatId, "Nechta fasl bo'ladi?");
    }

    private void askEpisodeCount(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_EPISODE_COUNT);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        sendText(chatId, pending.getCurrentSeasonNumber() + "-fasl uchun nechta qism bo'ladi?");
    }

    private void askEpisodeVideo(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_EPISODE_VIDEO);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        sendText(chatId, pending.getCurrentSeasonNumber() + "-fasl, " + pending.getCurrentEpisodeNumber() + "-qism uchun video yuboring:");
    }

}