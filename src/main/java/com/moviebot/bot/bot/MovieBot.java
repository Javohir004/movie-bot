package com.moviebot.bot.bot;

import com.moviebot.bot.domain.*;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.enums.InviteStatus;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.service.*;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final AdminInviteService adminInviteService;
    private final Long rootAdminId;



    public MovieBot(@Value("${bot.token}") String token,
                    @Value("${bot.username}") String username,
                    @Value("${bot.root-admin-id}") Long rootAdminId,
                    MovieService movieService,
                    UserService userService,
                    AdminSessionManager sessionManager,
                    SeasonService seasonService,
                    EpisodeService episodeService,
                    AdminInviteService adminInviteService) {
        super(token);
        this.username = username;
        this.rootAdminId = rootAdminId;
        this.movieService = movieService;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.seasonService = seasonService;
        this.episodeService = episodeService;
        this.adminInviteService = adminInviteService;
    }


    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            processUpdate(update);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }

    }

    private void processUpdate(Update update) {
        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getFrom() == null) return;

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

            if (data.equals("USER:TOP")) {
                sendTopViewed(chatId);
                return;
            }

            if (data.startsWith("WATCHMOVIE:")) {
                String code = data.substring("WATCHMOVIE:".length());
                sendActualMovieVideo(chatId, code);
                return;
            }

            if (data.startsWith("MOVIE:")) {
                String code = data.substring("MOVIE:".length());
                sendMovieByCode(chatId, code);
                return;
            }

            if (data.startsWith("SERIES:")) {
                String code = data.substring("SERIES:".length());
                movieService.findByCode(code).ifPresent(m -> sendSeasonList(chatId, m));
                return;
            }

            if (data.startsWith("SEASON:")) {
                Long seasonId = Long.parseLong(data.substring("SEASON:".length()));
                sendEpisodeList(chatId, seasonId);
                return;
            }

            if (data.startsWith("EPISODE:")) {
                Long episodeId = Long.parseLong(data.substring("EPISODE:".length()));
                sendEpisodeVideo(chatId, episodeId);
                return;
            }

            if (data.startsWith("INVITEOK:")) {
                Long inviteId = Long.parseLong(data.substring("INVITEOK:".length()));
                handleInviteConfirm(chatId, userId, inviteId);
                return;
            }

            if (data.startsWith("INVITENO:")) {
                Long inviteId = Long.parseLong(data.substring("INVITENO:".length()));
                handleInviteReject(chatId, userId, inviteId);
                return;
            }

            if (userService.isAdmin(userId)) {
                handleAdminCallback(chatId, userId, data);
            }

            return;
        }

        if (update.hasMessage() && update.getMessage().hasPhoto()) {
            if (update.getMessage().getFrom() == null) return;

            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();

            if (userService.isAdmin(userId)) {
                AdminState state = sessionManager.getState(userId);
                List<PhotoSize> photos = update.getMessage().getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();

                if (state == AdminState.AWAITING_POSTER) {
                    handleMoviePoster(chatId, userId, fileId);
                } else if (state == AdminState.AWAITING_SEASON_POSTER) {
                    handleSeasonPoster(chatId, userId, fileId);
                } else if (state == AdminState.AWAITING_SEASON_POSTER_EDIT) {
                    handleSeasonPosterEdit(chatId, userId, fileId);
                }
            }
            return;
        }

        if (update.hasMessage() && update.getMessage().hasVideo()) {
            if (update.getMessage().getFrom() == null) return;

            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();

            if (userService.isAdmin(userId)) {
                AdminState state = sessionManager.getState(userId);
                String fileId = update.getMessage().getVideo().getFileId();

                if (state == AdminState.AWAITING_VIDEO) {
                    finishAddMovie(chatId, userId, fileId);
                } else if (state == AdminState.AWAITING_EPISODE_VIDEO) {
                    handleEpisodeVideo(chatId, userId, fileId);
                } else if (state == AdminState.AWAITING_NEW_EPISODE_VIDEO) {
                    handleNewEpisodeVideo(chatId, userId, fileId);
                }
            }
            return;
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            if (update.getMessage().getFrom() == null) return;

            long userId = update.getMessage().getFrom().getId();
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText();

            String firstName = update.getMessage().getFrom().getFirstName();
            userService.registerOrUpdateName(userId, firstName);

            if (text.startsWith("/start")) {
                String[] startParts = text.trim().split("\\s+", 2);

                if (startParts.length > 1) {
                    handleInviteStart(chatId, userId, firstName, startParts[1]);
                    return;
                }

                if (userService.isAdmin(userId)) {
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
            AdminInvite invite = adminInviteService.createInvite(userId);
            String link = "https://t.me/" + username + "?start=" + invite.getToken();

            sendText(chatId, "Yangi admin qo'shish uchun havola (10 daqiqa amal qiladi):\n\n"
                    + link
                    + "\n\nUshbu havolani kerakli odamga yuboring. U bosgandan keyin sizga tasdiqlash so'rovi keladi.");
            return;
        }

        if (data.equals("ADMIN:LIST_ADMINS")) {
            sendAdminList(chatId);
            return;
        }

        if (data.equals("NOOP")) {
            return;
        }

        if (data.startsWith("REMOVEADMIN:")) {
            Long targetId = Long.parseLong(data.substring("REMOVEADMIN:".length()));
            handleRemoveAdmin(chatId, userId, targetId);
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

        if (data.startsWith("ADDSEASON:")) {
            startAddSeasonFlow(chatId, userId, data.substring("ADDSEASON:".length()));
            return;
        }

        if (data.startsWith("ADMIN_SEASONS:")) {
            startManageSeasonsFlow(chatId, data.substring("ADMIN_SEASONS:".length()));
            return;
        }

        if (data.startsWith("SEASONMANAGE:")) {
            Long seasonId = Long.parseLong(data.substring("SEASONMANAGE:".length()));
            sendSeasonManageMenu(chatId, seasonId);
            return;
        }

        if (data.startsWith("SEASONPOSTER:")) {
            Long seasonId = Long.parseLong(data.substring("SEASONPOSTER:".length()));
            startEditSeasonPosterFlow(chatId, userId, seasonId);
            return;
        }

        if (data.startsWith("ADDEPISODE:")) {
            Long seasonId = Long.parseLong(data.substring("ADDEPISODE:".length()));
            startAddSingleEpisodeFlow(chatId, userId, seasonId);
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
                askPoster(chatId, userId);
            }
            case AWAITING_POSTER -> {
                if (pending.isEditing() && text.trim().equalsIgnoreCase("/skip")) {
                    pending.setPosterFileId(null);
                    proceedAfterPoster(chatId, userId);
                } else {
                    sendText(chatId, "Iltimos, rasm (poster) yuboring" + (pending.isEditing() ? " yoki /skip yozing." : "."));
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
                    pending.setSeasonsProcessed(0);
                    askSeasonPoster(chatId, userId);
                } catch (NumberFormatException e) {
                    sendText(chatId, "Iltimos, musbat butun son kiriting (masalan 3):");
                }
            }
            case AWAITING_SEASON_POSTER -> sendText(chatId, "Iltimos, rasm (poster) yuboring.");
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

        int seasonsProcessed = pending.getSeasonsProcessed() + 1;

        if (seasonsProcessed < pending.getSeasonCount()) {
            pending.setSeasonsProcessed(seasonsProcessed);
            pending.setCurrentSeasonNumber(pending.getCurrentSeasonNumber() + 1);
            pending.setSeasonPosterFileId(null);
            askSeasonPoster(chatId, userId);
            return;
        }

        sendText(chatId, "✅ \"" + pending.getTitle() + "\" uchun fasl/qismlar qo'shildi!");
        sessionManager.clear(userId);
        sendAdminMenu(chatId);
    }

    private void handleAdminCommand(long chatId, long userId, String text) {
        sendText(chatId, "Noma'lum admin buyrug'i. Admin panel orqali foydalaning.");
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

    private void handleSeasonPosterEdit(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        Season season = seasonService.getById(pending.getCurrentSeasonId());

        seasonService.updatePoster(season, fileId);

        sessionManager.clear(userId);
        sendText(chatId, "✅ Poster yangilandi.");
        sendSeasonManageMenu(chatId, season.getId());
    }

    private void handleNewEpisodeVideo(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        Season season = seasonService.getById(pending.getCurrentSeasonId());

        episodeService.create(season, pending.getCurrentEpisodeNumber(), fileId);

        sessionManager.clear(userId);
        sendText(chatId, "✅ " + pending.getCurrentEpisodeNumber() + "-qism qo'shildi!");
        sendSeasonManageMenu(chatId, season.getId());
    }

    private void handleMoviePoster(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setPosterFileId(fileId);
        proceedAfterPoster(chatId, userId);
    }

    private void handleSeasonPoster(long chatId, long userId, String fileId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setSeasonPosterFileId(fileId);
        askEpisodeCount(chatId, userId);
    }

    private void handleInviteStart(long chatId, long userId, String firstName, String token) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findValidPendingInvite(token);

        if (inviteOpt.isEmpty()) {
            sendText(chatId, "Havola yaroqsiz yoki muddati tugagan.");
            return;
        }

        AdminInvite invite = inviteOpt.get();
        adminInviteService.markAwaitingConfirmation(invite, userId, firstName);

        sendText(chatId, "So'rovingiz yuborildi. Admin tasdiqlashini kuting.");

        String notifyText = "👤 " + firstName + " (id: " + userId + ") sizning havolangiz orqali admin bo'lishni so'ramoqda.\n\nTasdiqlaysizmi?";
        sendTextWithMarkup(invite.getCreatedByUserId(), notifyText, Keyboards.inviteConfirmMenu(invite.getId()));
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

    private void handleInviteConfirm(long chatId, long userId, Long inviteId) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findById(inviteId);

        if (inviteOpt.isEmpty()) {
            sendText(chatId, "So'rov topilmadi.");
            return;
        }

        AdminInvite invite = inviteOpt.get();

        if (!invite.getCreatedByUserId().equals(userId)) {
            sendText(chatId, "Bu so'rov sizga tegishli emas.");
            return;
        }

        if (invite.getStatus() != InviteStatus.AWAITING_CONFIRMATION) {
            sendText(chatId, "Bu so'rov allaqachon hal qilingan.");
            return;
        }

        userService.makeAdmin(invite.getTargetUserId());
        adminInviteService.confirm(invite);

        sendText(chatId, "✅ " + invite.getTargetName() + " admin qilindi.");
        sendAdminMenu(chatId);
        sendText(invite.getTargetUserId(), "🎉 Tabriklaymiz! Siz endi administrator. /start bosing.");
    }

    private void handleInviteReject(long chatId, long userId, Long inviteId) {
        Optional<AdminInvite> inviteOpt = adminInviteService.findById(inviteId);

        if (inviteOpt.isEmpty()) {
            sendText(chatId, "So'rov topilmadi.");
            return;
        }

        AdminInvite invite = inviteOpt.get();

        if (!invite.getCreatedByUserId().equals(userId)) {
            sendText(chatId, "Bu so'rov sizga tegishli emas.");
            return;
        }

        if (invite.getStatus() != InviteStatus.AWAITING_CONFIRMATION) {
            sendText(chatId, "Bu so'rov allaqachon hal qilingan.");
            return;
        }

        adminInviteService.reject(invite);

        sendText(chatId, "❌ Rad etildi.");
        sendText(invite.getTargetUserId(), "So'rovingiz admin tomonidan rad etildi.");
    }

    private void handleRemoveAdmin(long chatId, long userId, Long targetId) {
        if (targetId.equals(rootAdminId)) {
            sendText(chatId, "Asosiy adminni olib tashlab bo'lmaydi.");
            return;
        }

        if (targetId == userId) {
            sendText(chatId, "O'zingizni adminlikdan olib tashlay olmaysiz.");
            return;
        }

        userService.makeUser(targetId);
        sendText(chatId, "✅ Foydalanuvchi adminlikdan olindi.");
        sendAdminList(chatId);
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

    private void startAddSeasonFlow(long chatId, long userId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
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

        sendText(chatId, "\"" + movie.getTitle() + "\" uchun nechta yangi fasl qo'shmoqchisiz?");
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_COUNT);
    }

    private void startManageSeasonsFlow(long chatId, String movieCode) {
        Optional<Movie> movieOpt = movieService.findByCode(movieCode);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();
        List<Season> seasons = seasonService.getSeasonsForMovie(movie.getId());

        if (seasons.isEmpty()) {
            sendText(chatId, "Hali fasl qo'shilmagan.");
            return;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), "Faslni tanlang:");
        message.setReplyMarkup(Keyboards.seasonManageListMenu(seasons, movie.getCode()));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void startEditSeasonPosterFlow(long chatId, long userId, Long seasonId) {
        sessionManager.clear(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setCurrentSeasonId(seasonId);
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_POSTER_EDIT);

        sendText(chatId, "Yangi poster (rasm) yuboring:");
    }

    private void startAddSingleEpisodeFlow(long chatId, long userId, Long seasonId) {
        Season season = seasonService.getById(seasonId);
        int existingEpisodes = episodeService.getEpisodesForSeason(seasonId).size();

        sessionManager.clear(userId);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setCurrentSeasonId(seasonId);
        pending.setCurrentEpisodeNumber(existingEpisodes + 1);
        sessionManager.setState(userId, AdminState.AWAITING_NEW_EPISODE_VIDEO);

        sendText(chatId, season.getSeasonNumber() + "-fasl, " + (existingEpisodes + 1) + "-qism uchun video yuboring:");
    }


    /// .....
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
                sendText(chatId, "Bekor qilindi.");
                sendAdminMenu(chatId);
            }
        }
    }

    private void cancelSeriesCreation(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.getMovieId() != null && !pending.isSeasonAdditionOnly()) {
            movieService.deleteByCode(pending.getCode());
        }

        sessionManager.clear(userId);
        sendText(chatId, "Bekor qilindi.");
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
                    fileId,
                    pending.getPosterFileId()
            );
            sendText(chatId, "✅ \"" + pending.getTitle() + "\" yangilandi!");
        } else {
            movieService.save(
                    pending.getTitle(),
                    pending.getCode(),
                    pending.getType(),
                    pending.getDescription(),
                    fileId,
                    pending.getPosterFileId()
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
        message.setReplyMarkup(Keyboards.movieDetailMenu(movie));   // ← MANA SHU QATOR

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

    private void sendAdminList(long chatId) {
        List<User> admins = userService.getAllAdmins();

        SendMessage message = new SendMessage(String.valueOf(chatId), "Adminlar ro'yxati:");
        message.setReplyMarkup(Keyboards.adminListMenu(admins, rootAdminId));

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

        if (movie.getType() == MovieType.MOVIE) {
            sendMoviePosterCard(chatId, movie);
        } else {
            sendSeasonList(chatId, movie);
        }
    }

    private void sendMoviePosterCard(long chatId, Movie movie) {
        String caption = buildMovieCaption(movie);
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.moviePosterMenu(movie.getCode()));
    }

    private void sendEpisodeList(long chatId, Long seasonId) {
        Season season = seasonService.getById(seasonId);
        List<Episode> episodes = episodeService.getEpisodesForSeason(seasonId);

        seasonService.incrementViewCount(season);

        String caption = buildSeasonCaption(season);
        InlineKeyboardMarkup markup = Keyboards.episodeGridMenu(episodes, season.getMovie().getCode());

        if (season.getPosterFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), caption);
            message.setParseMode("HTML");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(season.getPosterFileId()));
        photo.setCaption(caption);
        photo.setParseMode("HTML");
        photo.setReplyMarkup(markup);

        try {
            execute(photo);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
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
        video.setCaption(season.getMovie().getTitle() + "\n" + season.getSeasonNumber() + "-fasl, " + episode.getEpisodeNumber() + "-qism");
        video.setReplyMarkup(Keyboards.episodeVideoMenu(season.getId(), nextEpisodeId));

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

    private void sendTextWithMarkup(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setReplyMarkup(markup);

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

    private void sendSeasonList(long chatId, Movie movie) {
        List<Season> seasons = seasonService.getSeasonsForMovie(movie.getId());

        if (seasons.isEmpty()) {
            String caption = buildMovieCaption(movie) + "\n\n(Hali fasllar yuklanmagan)";
            sendMovieCaptionCard(chatId, movie, caption, Keyboards.backToUserMenu());
            return;
        }

        String caption = buildMovieCaption(movie) + "\n\nFaslni tanlang:";
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.seasonListMenu(seasons));
    }

    private void sendMovieCaptionCard(long chatId, Movie movie, String caption, InlineKeyboardMarkup markup) {
        if (movie.getPosterFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), caption);
            message.setParseMode("HTML");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        SendPhoto photo = new SendPhoto();
        photo.setChatId(String.valueOf(chatId));
        photo.setPhoto(new InputFile(movie.getPosterFileId()));
        photo.setCaption(caption);
        photo.setParseMode("HTML");
        photo.setReplyMarkup(markup);

        try {
            execute(photo);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendActualMovieVideo(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, "Kino topilmadi.");
            return;
        }

        Movie movie = movieOpt.get();

        if (movie.getFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), "(Video hali yuklanmagan)");
            message.setReplyMarkup(Keyboards.backToUserMenu());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        movieService.incrementViewCount(movie);

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(movie.getFileId()));
        video.setCaption(movie.getTitle());
        video.setReplyMarkup(Keyboards.backToUserMenu());

        try {
            execute(video);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendSeasonManageMenu(long chatId, Long seasonId) {
        Season season = seasonService.getById(seasonId);

        SendMessage message = new SendMessage(String.valueOf(chatId), season.getSeasonNumber() + "-fasl:");
        message.setReplyMarkup(Keyboards.seasonManageMenu(seasonId, season.getMovie().getCode()));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTopViewed(long chatId) {
        List<Movie> movies = movieService.findAll();

        List<Movie> top = movies.stream()
                .sorted(Comparator.comparingInt(this::effectiveViewCount).reversed())
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            sendText(chatId, "Hozircha hech qanday kino yo'q.");
            return;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), "🔥 Eng ko'p ko'rilganlar:");
        message.setReplyMarkup(Keyboards.userMovieListMenu(top));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private int effectiveViewCount(Movie movie) {
        if (movie.getType() == MovieType.MOVIE) {
            return movie.getViewCount();
        }

        return seasonService.getSeasonsForMovie(movie.getId())
                .stream()
                .mapToInt(Season::getViewCount)
                .sum();
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

    private void askPoster(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_POSTER);
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        String note = pending.isEditing() ? "\n\nEski posterni saqlab qolish uchun /skip yozing." : "";

        SendMessage message = new SendMessage(String.valueOf(chatId), "Endi poster (rasm) yuboring:" + note);
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void askSeasonPoster(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_SEASON_POSTER);
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        SendMessage message = new SendMessage(String.valueOf(chatId),
                pending.getCurrentSeasonNumber() + "-fasl uchun poster (rasm) yuboring:");
        message.setReplyMarkup(Keyboards.backOnly());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }



    private String buildMovieCaption(Movie movie) {
        return "🎬 <b>" + escape(movie.getTitle()) + "</b>\n\n"
                + "📖 " + escape(movie.getDescription()) + "\n\n"
                + "👁 Ko'rishlar: " + formatCount(effectiveViewCount(movie)) + "\n"
                + "🆔 Kodi: " + movie.getCode();
    }

    private String buildSeasonCaption(Season season) {
        return "🎬 <b>" + escape(season.getMovie().getTitle()) + "</b>\n"
                + season.getSeasonNumber() + "-fasl\n\n"
                + "📖 " + escape(season.getMovie().getDescription()) + "\n\n"
                + "👁 Ko'rishlar: " + formatCount(season.getViewCount()) + "\n"
                + "🆔 Kodi: " + season.getMovie().getCode();
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String formatCount(int count) {
        if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }
}