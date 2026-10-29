package com.moviebot.bot.bot;

import com.moviebot.bot.domain.*;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.enums.InviteStatus;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.service.*;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.groupadministration.CreateChatInviteLink;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChat;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.ChatJoinRequest;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MovieBot extends TelegramLongPollingBot {

    // callback data 64 bayt bilan cheklangan ("ADMIN_SEASONS:" + kod ham shunga sig'ishi kerak)
    private static final int MAX_CODE_LENGTH = 20;
    private static final String CODE_PATTERN = "[A-Za-z0-9_-]{1," + MAX_CODE_LENGTH + "}";
    // Telegram rasm/video caption'i uchun limit
    private static final int MAX_CAPTION_LENGTH = 1024;
    // keyin qo'shiladigan "Faslni tanlang:" kabi matnlar uchun zaxira
    private static final int CAPTION_RESERVE = 60;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String BROADCAST_BUTTON_EXAMPLES = "• @mykanal\n"
            + "• Kanalga o'tish @mykanal\n"
            + "• Saytga o'tish https://example.uz";
    // messages*.properties dagi notfound.N kalitlari soni
    private static final int NOT_FOUND_VARIANTS = 5;

    private final String username;
    private final MovieService movieService;
    private final UserService userService;
    private final AdminSessionManager sessionManager;
    private final SeasonService seasonService;
    private final EpisodeService episodeService;
    private final AdminInviteService adminInviteService;
    private final RequiredChannelService requiredChannelService;
    private final StatsService statsService;
    private final BroadcastService broadcastService;
    private final GenreService genreService;
    private final I18n i18n;
    private final Long rootAdminId;
    private final String groupLink;

    public MovieBot(@Value("${bot.token}") String token,
                    @Value("${bot.username}") String username,
                    @Value("${bot.root-admin-id}") Long rootAdminId,
                    @Value("${bot.group-link}") String groupLink,
                    MovieService movieService,
                    UserService userService,
                    AdminSessionManager sessionManager,
                    SeasonService seasonService,
                    EpisodeService episodeService,
                    AdminInviteService adminInviteService,
                    RequiredChannelService requiredChannelService,
                    StatsService statsService,
                    BroadcastService broadcastService,
                    GenreService genreService,
                    I18n i18n) {
        super(token);
        this.i18n = i18n;
        this.statsService = statsService;
        this.broadcastService = broadcastService;
        this.genreService = genreService;
        this.username = username;
        this.rootAdminId = rootAdminId;
        this.movieService = movieService;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.seasonService = seasonService;
        this.episodeService = episodeService;
        this.adminInviteService = adminInviteService;
        this.requiredChannelService = requiredChannelService;
        this.groupLink = groupLink;
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
            log.error("Update'ni qayta ishlashda xato: updateId={}", update.getUpdateId(), e);
        }

        Long userId = extractUserId(update);
        if (userId == null) return;

        try {
            sessionManager.persist(userId);
        } catch (Exception e) {
            log.error("Admin sessiyasini saqlashda xato: updateId={}", update.getUpdateId(), e);
        }

        try {
            userService.touch(userId);
        } catch (Exception e) {
            log.error("Foydalanuvchi faolligini belgilashda xato: userId={}", userId, e);
        }
    }

    private Long extractUserId(Update update) {
        if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
            return update.getCallbackQuery().getFrom().getId();
        }
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            return update.getMessage().getFrom().getId();
        }
        if (update.hasChatJoinRequest() && update.getChatJoinRequest().getUser() != null) {
            return update.getChatJoinRequest().getUser().getId();
        }
        return null;
    }

    private void processUpdate(Update update) {
        if (update.hasChatJoinRequest()) {
            handleJoinRequest(update.getChatJoinRequest());
            return;
        }

        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getFrom() == null) return;

            answerCallback(update.getCallbackQuery().getId());

            String data = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            long userId = update.getCallbackQuery().getFrom().getId();
            Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

            if (data.startsWith("LANG:")) {
                String language = I18n.normalize(data.substring("LANG:".length()));
                userService.setLanguage(userId, language);
                sendText(chatId, t(chatId, "lang.saved"));
                if (userService.isAdmin(userId) || ensureSubscribed(chatId, userId)) {
                    sendUserStart(chatId);
                }
                return;
            }

            if (data.equals("USER:LANGUAGE")) {
                sendLanguageChoice(chatId);
                return;
            }

            if (data.equals("USER:MENU")) {
                sendUserStart(chatId);
                return;
            }

            if (data.equals("USER:SEARCH")) {
                if (!ensureSubscribed(chatId, userId)) return;

                SendMessage message = new SendMessage(String.valueOf(chatId), t(chatId, "search.prompt"));
                message.setReplyMarkup(Keyboards.backToUserMenu(tr(chatId)));
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    log.error("Telegram so'rovi bajarilmadi", e);
                }
                return;
            }

            if (data.equals("USER:LIST_ALL")) {
                if (!ensureSubscribed(chatId, userId)) return;

                sendUserMovieList(chatId, 0, null);
                return;
            }

            if (data.startsWith("USERLIST:")) {
                if (!ensureSubscribed(chatId, userId)) return;

                int page = Integer.parseInt(data.substring("USERLIST:".length()));
                sendUserMovieList(chatId, page, messageId);
                return;
            }

            if (data.equals("USER:GENRES")) {
                if (!ensureSubscribed(chatId, userId)) return;

                sendUserGenres(chatId);
                return;
            }

            if (data.startsWith("GENRE:")) {
                if (!ensureSubscribed(chatId, userId)) return;

                sendGenreMovies(chatId, Long.parseLong(data.substring("GENRE:".length())), 0, null);
                return;
            }

            // UGP:<janrId>:<sahifa>
            if (data.startsWith("UGP:")) {
                if (!ensureSubscribed(chatId, userId)) return;

                String[] parts = data.split(":");
                sendGenreMovies(chatId, Long.parseLong(parts[1]), Integer.parseInt(parts[2]), messageId);
                return;
            }

            if (data.equals("USER:TOP")) {
                if (!ensureSubscribed(chatId, userId)) return;

                sendTopViewed(chatId);
                return;
            }

            if (data.equals("CHECKSUB")) {
                if (ensureSubscribed(chatId, userId)) {
                    sendUserStart(chatId);
                }
                return;
            }

            // eski xabardagi tugmalar orqali ham obunasiz kontent ko'rib bo'lmasin
            if (isContentCallback(data) && !ensureSubscribed(chatId, userId)) return;

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
                handleAdminCallback(chatId, userId, messageId, data);
            }

            return;
        }

        // Reklama kutilayotganda istalgan turdagi xabar (matn, rasm, video, GIF, fayl...) qabul qilinadi.
        // Buyruqlar (/start va h.k.) bundan mustasno.
        if (update.hasMessage() && update.getMessage().getFrom() != null) {
            Message message = update.getMessage();
            long userId = message.getFrom().getId();
            boolean command = message.hasText() && message.getText().startsWith("/");

            if (!command && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_CONTENT
                    && userService.isAdmin(userId)) {
                captureBroadcastContent(message.getChatId(), userId, message);
                return;
            }
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
                } else if (userService.findLanguage(userId).isEmpty()) {
                    // birinchi marta: avval til tanlanadi, obuna tekshiruvi tanlovdan keyin
                    sendLanguageChoice(chatId);
                } else if (ensureSubscribed(chatId, userId)) {
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
                sendText(chatId, t(chatId, "command.unknown"));
                return;
            }

            if (!userService.isAdmin(userId) && !ensureSubscribed(chatId, userId)) {
                return;
            }

            handleSearch(chatId, text);
        }
    }


    /// handle..
    private void handleAdminCallback(long chatId, long userId, Integer messageId, String data) {

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

        if (data.equals("ADMIN:CHANNELS")) {
            sendChannelList(chatId);
            return;
        }

        if (data.equals("ADMIN:ADD_CHANNEL")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_CHANNEL_USERNAME);
            sendText(chatId, "Kanalni yuboring:\n\n"
                    + "• Ochiq kanal — username (masalan @mychannel)\n"
                    + "• Yopiq kanal — kanal ID'si (masalan -1001234567890). Bot o'zi zayavkali havola yaratadi, "
                    + "zayavka yuborgan foydalanuvchi obuna bo'lgan hisoblanadi.\n\n"
                    + "Yopiq kanal ID'sini bilish: kanalni web.telegram.org/a saytida oching, manzil qatoridagi # dan keyingi raqam.\n\n"
                    + "⚠️ Bot shu kanalda admin bo'lishi shart (yopiq kanalda \"Foydalanuvchilarni taklif qilish\" huquqi bilan).");
            return;
        }

        if (data.startsWith("CHDUR:") && sessionManager.getState(userId) == AdminState.AWAITING_CHANNEL_DURATION) {
            finishAddChannel(chatId, userId, Integer.parseInt(data.substring("CHDUR:".length())));
            return;
        }

        if (data.startsWith("REMOVECHANNEL:")) {
            Long channelId = Long.parseLong(data.substring("REMOVECHANNEL:".length()));
            requiredChannelService.findById(channelId).ifPresentOrElse(
                    channel -> sendTextWithMarkup(chatId,
                            channel.displayName() + " kanalini olib tashlaysizmi?\n\n"
                                    + "👥 Bot orqali qo'shilganlar: " + requiredChannelService.joinCount(channelId)
                                    + "\n\nO'chirilgandan keyin bu hisob ham o'chadi.",
                            Keyboards.channelRemoveConfirmMenu(channelId)),
                    () -> sendChannelList(chatId));
            return;
        }

        if (data.startsWith("RMCHCONF:")) {
            Long channelId = Long.parseLong(data.substring("RMCHCONF:".length()));
            requiredChannelService.findById(channelId).ifPresent(channel -> {
                long joins = requiredChannelService.joinCount(channelId);
                requiredChannelService.remove(channelId);
                sendText(chatId, "✅ " + channel.displayName() + " olib tashlandi.\n👥 Bot orqali qo'shilganlar: " + joins);
            });
            sendChannelList(chatId);
            return;
        }

        if (data.equals("ADMIN:BROADCAST")) {
            sessionManager.clear(userId);
            sendTextWithMarkup(chatId, "📣 Reklama", Keyboards.broadcastMenu());
            return;
        }

        if (data.equals("ADMIN:BROADCAST_NEW")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_CONTENT);
            sendText(chatId, "Reklama xabarini yuboring: matn, rasm, video, GIF, fayl — istalgan turdagi xabar.\n"
                    + "U barcha foydalanuvchilarga xuddi shunday ko'rinishda yuboriladi.");
            return;
        }

        if (data.equals("ADMIN:BROADCAST_SCHEDULED")) {
            sendScheduledBroadcasts(chatId);
            return;
        }

        if (data.equals("ADMIN:BROADCAST_HISTORY")) {
            sendBroadcastHistory(chatId);
            return;
        }

        if (data.startsWith("BCASTCANCEL:")) {
            Long broadcastId = Long.parseLong(data.substring("BCASTCANCEL:".length()));
            sendText(chatId, broadcastService.cancel(broadcastId)
                    ? "✅ Reklama #" + broadcastId + " bekor qilindi."
                    : "Reklama #" + broadcastId + " allaqachon yuborila boshlagan yoki bekor qilingan.");
            sendScheduledBroadcasts(chatId);
            return;
        }

        if (data.equals("BCAST:NOBTN") && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_BUTTON) {
            showBroadcastPreview(chatId, userId);
            return;
        }

        // holat tekshiriladi: "Yuborish" ikki marta bosilsa ikkinchisi hech narsa qilmaydi
        if (data.equals("BROADCAST:SEND")) {
            if (sessionManager.getState(userId) != AdminState.AWAITING_BROADCAST_CONFIRM) {
                sendText(chatId, "Bu reklama allaqachon navbatga qo'yilgan yoki bekor qilingan.");
                return;
            }
            scheduleBroadcast(chatId, userId, LocalDateTime.now());
            return;
        }

        if (data.equals("BROADCAST:SCHEDULE") && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_CONFIRM) {
            sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_TIME);
            sendText(chatId, "Yuborish vaqtini yozing:\n\n"
                    + "• 20:00 — bugun (vaqt o'tib ketgan bo'lsa ertaga)\n"
                    + "• 30.10 20:00\n"
                    + "• 30.10.2026 20:00");
            return;
        }

        if (data.equals("BROADCAST:CANCEL")) {
            sessionManager.clear(userId);
            sendText(chatId, "Bekor qilindi.");
            sendAdminMenu(chatId);
            return;
        }

        if (data.equals("ADMIN:LIST_MOVIES")) {
            sendMovieList(chatId);
            return;
        }

        if (data.equals("ADMIN:STATS")) {
            sendHtml(chatId, statsService.buildReport(), Keyboards.statsMenu());
            return;
        }

        if (data.equals("ADMIN:GENRES")) {
            sessionManager.clear(userId);
            sendGenreAdminList(chatId);
            return;
        }

        if (data.equals("ADMIN:ADD_GENRE")) {
            sessionManager.clear(userId);
            sessionManager.setState(userId, AdminState.AWAITING_GENRE_NAME);
            sendTextWithMarkup(chatId, "Janr nomini yozing. Bir nechtasini vergul bilan ajratib yozsangiz bo'ladi:\n"
                    + "Komediya, Drama, Jangari", Keyboards.backOnly());
            return;
        }

        if (data.startsWith("GENREEDIT:")) {
            sessionManager.clear(userId);
            sendGenreEdit(chatId, Long.parseLong(data.substring("GENREEDIT:".length())));
            return;
        }

        // GENRETR:<janrId>:<til> — shu tildagi nomni yozish so'raladi
        if (data.startsWith("GENRETR:")) {
            String[] parts = data.split(":");
            Long genreId = Long.parseLong(parts[1]);
            String language = I18n.normalize(parts[2]);

            sessionManager.clear(userId);
            PendingMovie pending = sessionManager.getPendingMovie(userId);
            pending.setGenreTranslationId(genreId);
            pending.setGenreTranslationLanguage(language);
            sessionManager.setState(userId, AdminState.AWAITING_GENRE_TRANSLATION);

            String languageName = switch (language) {
                case "ru" -> "ruscha";
                case "en" -> "inglizcha";
                default -> "o'zbekcha";
            };
            sendTextWithMarkup(chatId, "Janrning " + languageName + " nomini yozing.\n"
                    + "Tarjimani o'chirish uchun - yuboring (unda asosiy nom ko'rsatiladi).", Keyboards.backOnly());
            return;
        }

        if (data.startsWith("RMGENRE:")) {
            Long genreId = Long.parseLong(data.substring("RMGENRE:".length()));
            genreService.findById(genreId).ifPresentOrElse(
                    genre -> sendTextWithMarkup(chatId, "\"" + genre.getName() + "\" janrini o'chirasizmi?\n"
                                    + "Kinolarning o'zi o'chmaydi, faqat ulardan bu janr olib tashlanadi.",
                            Keyboards.genreRemoveConfirmMenu(genreId)),
                    () -> sendGenreAdminList(chatId));
            return;
        }

        if (data.startsWith("RMGENRECONF:")) {
            Long genreId = Long.parseLong(data.substring("RMGENRECONF:".length()));
            genreService.findById(genreId).ifPresent(genre -> {
                genreService.remove(genreId);
                sendText(chatId, "✅ \"" + genre.getName() + "\" o'chirildi.");
            });
            sendGenreAdminList(chatId);
            return;
        }

        if (data.startsWith("MGENRES:")) {
            sendMovieGenreToggle(chatId, Long.parseLong(data.substring("MGENRES:".length())), null);
            return;
        }

        // MGT:<kinoId>:<janrId> — belgilash/olib tashlash, keyin o'sha xabarning tugmalari yangilanadi
        if (data.startsWith("MGT:")) {
            String[] parts = data.split(":");
            Long movieId = Long.parseLong(parts[1]);
            genreService.toggle(movieId, Long.parseLong(parts[2]));
            sendMovieGenreToggle(chatId, movieId, messageId);
            return;
        }

        if (data.startsWith("ADMINLIST:")) {
            int page = Integer.parseInt(data.substring("ADMINLIST:".length()));
            sendMovieList(chatId, page, messageId);
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
                String code = text.trim();
                boolean keepsOwnCode = pending.isEditing() && code.equals(pending.getEditingCode());

                if (!keepsOwnCode && !code.matches(CODE_PATTERN)) {
                    sendText(chatId, "Kod faqat lotin harflari, raqamlar, \"-\" va \"_\" dan iborat bo'lishi va "
                            + MAX_CODE_LENGTH + " belgidan oshmasligi kerak. Qaytadan kiriting:");
                    return;
                }

                // tahrirlashda o'z kodining faqat harf kattaligini o'zgartirish mumkin (A1 -> a1)
                boolean ownCodeAnyCase = pending.isEditing() && code.equalsIgnoreCase(pending.getEditingCode());
                if (!ownCodeAnyCase && movieService.isCodeTaken(code)) {
                    sendText(chatId, "Bu kod allaqachon band. Boshqa kod kiriting:");
                    return;
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
            case AWAITING_CHANNEL_USERNAME -> handleChannelInput(chatId, userId, text.trim());
            case AWAITING_GENRE_TRANSLATION -> {
                Long genreId = pending.getGenreTranslationId();
                String language = pending.getGenreTranslationLanguage();
                if (genreId == null || language == null) {
                    sessionManager.clear(userId);
                    sendGenreAdminList(chatId);
                    return;
                }

                try {
                    if (genreService.setTranslation(genreId, language, text).isEmpty()) {
                        sessionManager.clear(userId);
                        sendText(chatId, "Janr topilmadi.");
                        sendGenreAdminList(chatId);
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    sendText(chatId, "Nom " + GenreService.MAX_NAME_LENGTH + " belgidan oshmasligi kerak. Qaytadan yozing:");
                    return;
                }

                sessionManager.clear(userId);
                sendText(chatId, "✅ Saqlandi.");
                sendGenreEdit(chatId, genreId);
            }
            case AWAITING_GENRE_NAME -> {
                GenreService.AddResult result = genreService.addAll(text);
                sessionManager.clear(userId);

                StringBuilder reply = new StringBuilder();
                if (!result.added().isEmpty()) {
                    reply.append("✅ Qo'shildi: ").append(String.join(", ", result.added()));
                }
                if (!result.skipped().isEmpty()) {
                    if (!reply.isEmpty()) reply.append("\n");
                    reply.append("⚠️ O'tkazib yuborildi (allaqachon bor yoki ").append(GenreService.MAX_NAME_LENGTH)
                            .append(" belgidan uzun): ").append(String.join(", ", result.skipped()));
                }
                sendText(chatId, reply.isEmpty() ? "Hech qanday janr yozilmadi." : reply.toString());
                sendGenreAdminList(chatId);
            }
            case AWAITING_CHANNEL_DURATION -> {
                try {
                    int days = Integer.parseInt(text.trim());
                    if (days < 1 || days > 3650) throw new NumberFormatException();
                    finishAddChannel(chatId, userId, days);
                } catch (NumberFormatException e) {
                    sendText(chatId, "Kunlar sonini raqam bilan yozing (masalan 14) yoki tugmalardan birini tanlang.");
                }
            }
            case AWAITING_BROADCAST_CONFIRM -> sendText(chatId, "Iltimos, tugmalardan birini bosing.");
            case AWAITING_BROADCAST_BUTTON -> BroadcastInput.parseButton(text).ifPresentOrElse(button -> {
                pending.setBroadcastButtonText(button.text());
                pending.setBroadcastButtonUrl(button.url());
                showBroadcastPreview(chatId, userId);
            }, () -> sendText(chatId, "Havolani tushunmadim. Shunday yozing:\n\n"
                    + BROADCAST_BUTTON_EXAMPLES + "\n\n"
                    + "Havola @username, t.me/... yoki https://... bo'lishi, matn 64 belgidan oshmasligi kerak.\n"
                    + "Tugma kerak bo'lmasa, \"Tugmasiz davom etish\"ni bosing."));
            case AWAITING_BROADCAST_TIME -> BroadcastInput.parseTime(text, LocalDateTime.now()).ifPresentOrElse(
                    sendAt -> scheduleBroadcast(chatId, userId, sendAt),
                    () -> sendText(chatId, "Vaqtni tushunmadim. Shunday yozing: 20:00, 30.10 20:00 yoki 30.10.2026 20:00.\n"
                            + "Vaqt kelajakda va " + BroadcastInput.MAX_DAYS_AHEAD + " kun ichida bo'lishi kerak."));
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
        Long movieId = pending.getMovieId();
        sessionManager.clear(userId);
        // yangi serial bo'lsa janr taklif qilinadi; mavjud serialga fasl qo'shilganda shart emas
        if (movieId != null && !pending.isSeasonAdditionOnly()) {
            offerGenres(chatId, movieService.getById(movieId));
        }
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
        MovieService.SearchResult result = movieService.search(query);
        List<Movie> matches = result.matches();

        if (matches.size() == 1) {
            sendMovieByCode(chatId, matches.get(0).getCode());
        } else if (!matches.isEmpty()) {
            sendChoices(chatId, matches, matches.size() >= MovieService.MAX_RESULTS
                    ? t(chatId, "search.tooMany", MovieService.MAX_RESULTS)
                    : t(chatId, "search.many"));
        } else if (!result.suggestions().isEmpty()) {
            sendChoices(chatId, result.suggestions(), t(chatId, "search.suggest"));
        } else {
            sendNotFound(chatId);
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
        sendText(chatId, message);
        sendAdminMenu(chatId);
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
            sendText(chatId, "✅ \"" + pending.getTitle() + "\" yangilandi!");
        } else {
            movie = movieService.save(
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
        offerGenres(chatId, movie);
        sendAdminMenu(chatId);
    }

    // kino qo'shilgandan keyin janr belgilashni taklif qiladi (janrlar bo'lsa)
    private void offerGenres(long chatId, Movie movie) {
        if (movie == null || genreService.findAll().isEmpty()) return;

        sendTextWithMarkup(chatId, "🎭 \"" + movie.getTitle() + "\" uchun janrlarni belgilaysizmi?",
                Keyboards.linkToGenres(movie.getId()));
    }

    private void sendMovieGenreToggle(long chatId, Long movieId, Integer editMessageId) {
        Movie movie = movieService.getById(movieId);
        List<Genre> genres = genreService.findAll();

        if (genres.isEmpty()) {
            sendTextWithMarkup(chatId, "Hali janrlar qo'shilmagan. Avval admin paneldagi 🎭 Janrlar bo'limidan qo'shing.",
                    Keyboards.adminMenu());
            return;
        }

        InlineKeyboardMarkup markup = Keyboards.movieGenreToggleMenu(movieId, movie.getCode(), genres,
                new HashSet<>(genreService.genreIdsForMovie(movieId)));
        sendOrEditList(chatId, editMessageId, "🎭 \"" + movie.getTitle() + "\" janrlari (bosib belgilang):", markup);
    }

    private void sendGenreEdit(long chatId, Long genreId) {
        Optional<Genre> genreOpt = genreService.findById(genreId);
        if (genreOpt.isEmpty()) {
            sendGenreAdminList(chatId);
            return;
        }

        Genre genre = genreOpt.get();
        String text = "🎭 <b>" + escape(genre.getName()) + "</b>\n\n"
                + "🇺🇿 O'zbekcha: " + translationOrDash(genre.getNameUz()) + "\n"
                + "🇷🇺 Русский: " + translationOrDash(genre.getNameRu()) + "\n"
                + "🇬🇧 English: " + translationOrDash(genre.getNameEn()) + "\n\n"
                + "<i>Tarjima yozilmagan tilda asosiy nom (" + escape(genre.getName()) + ") ko'rsatiladi.</i>";
        sendHtml(chatId, text, Keyboards.genreEditMenu(genreId));
    }

    private String translationOrDash(String value) {
        return value == null || value.isBlank() ? "—" : escape(value);
    }

    private void sendGenreAdminList(long chatId) {
        var genres = genreService.findAllWithMovieCount();
        String text = genres.isEmpty()
                ? "🎭 Janrlar hali qo'shilmagan.\n\nQo'shilgan janrlar foydalanuvchi menyusida chiqadi, kinolarga esa kartochkasidagi 🎭 tugma orqali belgilanadi."
                : "🎭 Janrlar (qavs ichida kinolar soni).\n🌐 — uchala tilga tarjima qilingan, ⚠️ — tarjimasi chala.\n"
                + "Tarjima qilish yoki o'chirish uchun janrni bosing:";
        sendTextWithMarkup(chatId, text, Keyboards.genreAdminMenu(genres));
    }

    private void sendUserGenres(long chatId) {
        String language = lang(chatId);
        // foydalanuvchi tilidagi nom bo'yicha alifbo tartibida
        var genres = genreService.findNonEmpty().stream()
                .sorted(Comparator.comparing(g -> g.nameFor(language), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (genres.isEmpty()) {
            sendTextWithMarkup(chatId, t(chatId, "genres.empty"), Keyboards.backToUserMenu(tr(chatId)));
            return;
        }
        sendTextWithMarkup(chatId, t(chatId, "genres.choose"), Keyboards.userGenreMenu(genres, tr(chatId), language));
    }

    private void sendGenreMovies(long chatId, Long genreId, int page, Integer editMessageId) {
        Optional<Genre> genre = genreService.findById(genreId);
        List<Movie> movies = movieService.findByGenre(genreId);

        if (genre.isEmpty() || movies.isEmpty()) {
            sendUserGenres(chatId);
            return;
        }
        sendOrEditList(chatId, editMessageId, "🎭 " + genre.get().nameFor(lang(chatId)) + ":",
                Keyboards.userGenreMoviePage(movies, genreId, page, tr(chatId)));
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
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void confirmDelete(long chatId, String code) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Rostdan ham o'chirmoqchimisiz?");
        message.setReplyMarkup(Keyboards.deleteConfirmMenu(code));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }


    ///  send...
    private void sendAdminMenu(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Admin panel:");
        message.setReplyMarkup(Keyboards.adminMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendAdminList(long chatId) {
        List<User> admins = userService.getAllAdmins();

        SendMessage message = new SendMessage(String.valueOf(chatId), "Adminlar ro'yxati:");
        message.setReplyMarkup(Keyboards.adminListMenu(admins, rootAdminId));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
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

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(rows);

        SendMessage message = new SendMessage(String.valueOf(chatId), header);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendMovieByCode(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, t(chatId, "movie.notFound"));
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
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.moviePosterMenu(movie.getCode(), tr(chatId)));
    }

    private void sendEpisodeList(long chatId, Long seasonId) {
        Optional<Season> seasonOpt = seasonService.findById(seasonId);

        if (seasonOpt.isEmpty()) {
            sendText(chatId, t(chatId, "season.gone"));
            return;
        }

        Season season = seasonOpt.get();
        List<Episode> episodes = episodeService.getEpisodesForSeason(seasonId);

        seasonService.incrementViewCount(season);

        boolean singleSeason = seasonService.countSeasonsForMovie(season.getMovie().getId()) <= 1;

        String caption = buildSeasonCaption(season, chatId);
        InlineKeyboardMarkup markup = Keyboards.episodeGridMenu(episodes, season.getMovie().getCode(), singleSeason, tr(chatId));

        if (season.getPosterFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), caption);
            message.setParseMode("HTML");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
        video.setCaption(season.getMovie().getTitle() + "\n"
                + t(chatId, "episode.caption", season.getSeasonNumber(), episode.getEpisodeNumber())
                + "\n\n🤖 @" + username);
        video.setReplyMarkup(Keyboards.episodeVideoMenu(season.getId(), nextEpisodeId, tr(chatId)));

        try {
            execute(video);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void answerCallback(String callbackQueryId) {
        try {
            execute(new AnswerCallbackQuery(callbackQueryId));
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendText(long chatId, String text) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendHtml(long chatId, String html, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), html);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendTextWithMarkup(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendMovieList(long chatId) {
        sendMovieList(chatId, 0, null);
    }

    private void sendMovieList(long chatId, int page, Integer editMessageId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sendText(chatId, "Hozircha hech qanday kino qo'shilmagan.");
            return;
        }

        sendOrEditList(chatId, editMessageId, "Barcha kino/seriallar:", Keyboards.movieListMenu(movies, page));
    }

    private void sendUserMovieList(long chatId, int page, Integer editMessageId) {
        List<Movie> movies = movieService.findAll();

        if (movies.isEmpty()) {
            sendText(chatId, t(chatId, "list.empty"));
            return;
        }

        sendOrEditList(chatId, editMessageId, t(chatId, "list.all"), Keyboards.userMovieListPage(movies, page, tr(chatId)));
    }

    private void sendLanguageChoice(long chatId) {
        sendTextWithMarkup(chatId, "🌐 Tilni tanlang · Выберите язык · Choose a language", Keyboards.languageMenu());
    }

    // Sahifa almashtirilganda yangi xabar yubormasdan, mavjud xabarning tugmalarini almashtiradi
    private void sendOrEditList(long chatId, Integer editMessageId, String text, InlineKeyboardMarkup markup) {
        try {
            if (editMessageId != null) {
                EditMessageReplyMarkup edit = new EditMessageReplyMarkup();
                edit.setChatId(String.valueOf(chatId));
                edit.setMessageId(editMessageId);
                edit.setReplyMarkup(markup);
                execute(edit);
            } else {
                SendMessage message = new SendMessage(String.valueOf(chatId), text);
                message.setReplyMarkup(markup);
                execute(message);
            }
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendUserStart(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), t(chatId, "user.start"));
        message.setParseMode("HTML");
        message.setReplyMarkup(Keyboards.userMainMenu(tr(chatId)));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendSeasonList(long chatId, Movie movie) {
        List<Season> seasons = seasonService.getSeasonsForMovie(movie.getId());

        if (seasons.isEmpty()) {
            String caption = buildMovieCaption(movie, chatId) + "\n\n" + t(chatId, "caption.noSeasons");
            sendMovieCaptionCard(chatId, movie, caption, Keyboards.backToUserMenu(tr(chatId)));
            return;
        }

        if (seasons.size() == 1) {
            sendEpisodeList(chatId, seasons.get(0).getId());
            return;
        }

        String caption = buildMovieCaption(movie, chatId) + "\n\n" + t(chatId, "caption.chooseSeason");
        sendMovieCaptionCard(chatId, movie, caption, Keyboards.seasonListMenu(seasons, tr(chatId)));
    }

    private void sendMovieCaptionCard(long chatId, Movie movie, String caption, InlineKeyboardMarkup markup) {
        if (movie.getPosterFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), caption);
            message.setParseMode("HTML");
            message.setReplyMarkup(markup);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendActualMovieVideo(long chatId, String code) {
        Optional<Movie> movieOpt = movieService.findByCode(code);

        if (movieOpt.isEmpty()) {
            sendText(chatId, t(chatId, "movie.notFound"));
            return;
        }

        Movie movie = movieOpt.get();

        if (movie.getFileId() == null) {
            SendMessage message = new SendMessage(String.valueOf(chatId), t(chatId, "video.notUploaded"));
            message.setReplyMarkup(Keyboards.backToUserMenu(tr(chatId)));

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error("Telegram so'rovi bajarilmadi", e);
            }
            return;
        }

        movieService.incrementViewCount(movie);

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(movie.getFileId()));
        video.setCaption(movie.getTitle() + "\n\n🤖 @" + username);
        video.setReplyMarkup(Keyboards.backToUserMenu(tr(chatId)));

        try {
            execute(video);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendSeasonManageMenu(long chatId, Long seasonId) {
        Season season = seasonService.getById(seasonId);

        SendMessage message = new SendMessage(String.valueOf(chatId), season.getSeasonNumber() + "-fasl:");
        message.setReplyMarkup(Keyboards.seasonManageMenu(seasonId, season.getMovie().getCode()));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void sendTopViewed(long chatId) {
        List<Movie> movies = movieService.findAll();

        List<Movie> top = movies.stream()
                .sorted(Comparator.comparingInt(this::effectiveViewCount).reversed())
                .limit(5)
                .toList();

        if (top.isEmpty()) {
            sendText(chatId, t(chatId, "list.empty"));
            return;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), t(chatId, "list.top"));
        message.setReplyMarkup(Keyboards.userMovieListMenu(top, tr(chatId)));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
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

    private void sendNotFound(long chatId) {
        // messages*.properties dagi notfound.1 ... notfound.5 dan tasodifiy biri
        String randomMessage = t(chatId, "notfound." + (1 + new Random().nextInt(NOT_FOUND_VARIANTS)));

        sendText(chatId, randomMessage);
        sendUserStart(chatId);
    }

    private void sendChannelList(long chatId) {
        List<RequiredChannel> channels = requiredChannelService.getActiveChannels();
        StringBuilder text = new StringBuilder("📢 <b>Majburiy kanallar</b>\n\n");

        if (channels.isEmpty()) {
            text.append("Hozircha kanal qo'shilmagan.");
        }

        for (int i = 0; i < channels.size(); i++) {
            RequiredChannel c = channels.get(i);
            text.append(i + 1).append(". ").append(escape(c.displayName()));
            if (c.getTitle() != null && c.getChannelUsername() != null) {
                text.append(" (@").append(escape(c.getChannelUsername())).append(")");
            }
            if (c.isJoinRequest()) {
                text.append(" 🔒 zayavka");
            }
            text.append("\n    👥 Bot orqali: ").append(requiredChannelService.joinCount(c.getId()));
            text.append("\n    ⏳ ").append(describeExpiry(c.getExpiresAt())).append("\n\n");
        }

        sendHtml(chatId, text.toString(), Keyboards.channelListMenu(channels));
    }

    private String describeExpiry(LocalDateTime expiresAt) {
        if (expiresAt == null) return "Muddatsiz";
        long daysLeft = Math.max(0, java.time.Duration.between(LocalDateTime.now(), expiresAt).toDays());
        return expiresAt.format(DATE_TIME) + " gacha (" + daysLeft + " kun qoldi)";
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }

    private void askType(long chatId, long userId) {
        sessionManager.setState(userId, AdminState.AWAITING_TYPE);

        SendMessage message = new SendMessage(String.valueOf(chatId), "Turi qanday?");
        message.setReplyMarkup(Keyboards.movieTypeMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
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
            log.error("Telegram so'rovi bajarilmadi", e);
        }
    }



    private String buildMovieCaption(Movie movie, long chatId) {
        String header = "🎬 <b>" + escape(movie.getTitle()) + "</b>\n" + genresLine(movie, chatId) + "\n";
        String footer = "\n\n" + t(chatId, "caption.views", formatCount(effectiveViewCount(movie))) + "\n"
                + t(chatId, "caption.code", escape(movie.getCode()))
                + groupLine(chatId);
        return header + "📖 " + fitDescription(movie.getDescription(), header + footer) + footer;
    }

    private String buildSeasonCaption(Season season, long chatId) {
        Movie movie = season.getMovie();
        String header = "🎬 <b>" + escape(movie.getTitle()) + "</b>\n"
                + t(chatId, "caption.season", season.getSeasonNumber()) + "\n" + genresLine(movie, chatId) + "\n";
        String footer = "\n\n" + t(chatId, "caption.views", formatCount(season.getViewCount())) + "\n"
                + t(chatId, "caption.code", escape(movie.getCode()))
                + groupLine(chatId);
        return header + "📖 " + fitDescription(movie.getDescription(), header + footer) + footer;
    }

    // janr belgilanmagan bo'lsa bo'sh
    private String genresLine(Movie movie, long chatId) {
        List<String> names = genreService.namesForMovie(movie.getId(), lang(chatId));
        return names.isEmpty() ? "" : "🎭 " + escape(String.join(", ", names)) + "\n";
    }

    // BOT_GROUP_LINK berilmagan bo'lsa qator umuman chiqmaydi
    private String groupLine(long chatId) {
        return groupLink == null || groupLink.isBlank() ? "" : "\n\n" + t(chatId, "caption.group", escape(groupLink));
    }

    // Foydalanuvchi tili (tanlamagan bo'lsa o'zbekcha). Bot faqat shaxsiy chatda ishlaydi, shuning uchun chatId = userId.
    private String lang(long chatId) {
        return userService.findLanguage(chatId).orElse(I18n.DEFAULT_LANGUAGE);
    }

    private String t(long chatId, String key, Object... args) {
        return i18n.t(lang(chatId), key, args);
    }

    private java.util.function.Function<String, String> tr(long chatId) {
        return i18n.forLanguage(lang(chatId));
    }

    // Caption limitiga sig'adigan qilib tavsifni qisqartiradi
    private String fitDescription(String description, String rest) {
        int available = MAX_CAPTION_LENGTH - CAPTION_RESERVE - rest.length() - "📖 ".length();
        return TextFormat.fitDescription(description, available);
    }

    private String escape(String text) {
        return TextFormat.escape(text);
    }

    private String formatCount(int count) {
        return TextFormat.formatCount(count);
    }

    private boolean isMemberOf(RequiredChannel channel, long userId) {
        // zayavkali kanalda zayavka yuborganining o'zi yetarli (API'ga murojaat qilmasdan)
        if (channel.isJoinRequest() && requiredChannelService.hasJoinRequest(channel.getId(), userId)) {
            return true;
        }

        try {
            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(channel.chatIdentifier());
            getChatMember.setUserId(userId);
            ChatMember member = execute(getChatMember);
            String status = member.getStatus();
            return !"left".equals(status) && !"kicked".equals(status);
        } catch (TelegramApiException e) {
            // tekshira olmasak foydalanuvchini to'smaymiz, lekin admin bilishi uchun logga yozamiz
            log.warn("{} kanalida a'zolikni tekshirib bo'lmadi (bot admin emasmi?): {}", channel.chatIdentifier(), e.getMessage());
            return true;
        }
    }

    private boolean isContentCallback(String data) {
        return data.startsWith("WATCHMOVIE:")
                || data.startsWith("MOVIE:")
                || data.startsWith("SERIES:")
                || data.startsWith("SEASON:")
                || data.startsWith("EPISODE:");
    }

    // chatIdentifier: "@username" yoki "-100..."; null qaytarsa hammasi joyida, aks holda muammo matni
    private String checkBotIsChannelAdmin(String chatIdentifier) {
        try {
            Long botId = execute(new GetMe()).getId();

            GetChatMember getChatMember = new GetChatMember();
            getChatMember.setChatId(chatIdentifier);
            getChatMember.setUserId(botId);
            String status = execute(getChatMember).getStatus();

            if ("administrator".equals(status) || "creator".equals(status)) {
                return null;
            }
            return "bot bu kanalda admin emas.";
        } catch (TelegramApiException e) {
            return "kanal topilmadi yoki bot unga qo'shilmagan.";
        }
    }

    private String fetchChatTitle(String chatIdentifier) {
        try {
            return execute(GetChat.builder().chatId(chatIdentifier).build()).getTitle();
        } catch (TelegramApiException e) {
            log.warn("{} kanal nomini olib bo'lmadi: {}", chatIdentifier, e.getMessage());
            return null;
        }
    }

    // yopiq kanal uchun zayavkali havola; bot "Foydalanuvchilarni taklif qilish" huquqiga ega bo'lishi kerak
    private String createJoinRequestLink(String chatId) {
        try {
            return execute(CreateChatInviteLink.builder()
                    .chatId(chatId)
                    .name("Bot orqali")
                    .createsJoinRequest(true)
                    .build()).getInviteLink();
        } catch (TelegramApiException e) {
            log.warn("{} uchun havola yaratib bo'lmadi: {}", chatId, e.getMessage());
            return null;
        }
    }

    private void handleChannelInput(long chatId, long userId, String input) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (input.matches("-100\\d{5,}")) {
            String problem = checkBotIsChannelAdmin(input);
            if (problem != null) {
                sendText(chatId, "❌ Kanal qo'shilmadi: " + problem + "\n\nBotni kanalga admin qilib, qaytadan yuboring:");
                return;
            }
            if (requiredChannelService.exists(null, input)) {
                sendText(chatId, "Bu kanal allaqachon ro'yxatda. Boshqa kanal yuboring:");
                return;
            }

            String link = createJoinRequestLink(input);
            if (link == null) {
                sendText(chatId, "❌ Havola yaratib bo'lmadi. Botga kanalda \"Foydalanuvchilarni taklif qilish\" huquqini bering va ID'ni qaytadan yuboring:");
                return;
            }

            pending.setChannelChatId(input);
            pending.setChannelUsername(null);
            pending.setChannelInviteLink(link);
            pending.setChannelJoinRequest(true);
            pending.setChannelTitle(fetchChatTitle(input));
        } else {
            String cleaned = input
                    .replace("https://t.me/", "")
                    .replace("http://t.me/", "")
                    .replace("t.me/", "");
            if (cleaned.startsWith("@")) {
                cleaned = cleaned.substring(1);
            }

            if (!cleaned.matches("[A-Za-z0-9_]{4,32}")) {
                sendText(chatId, "Username yoki ID noto'g'ri. Masalan: @mychannel yoki -1001234567890. Qaytadan yuboring:");
                return;
            }

            // bot kanalda admin bo'lmasa, a'zolikni tekshira olmaydi va obuna talabi jimgina ishlamay qoladi
            String problem = checkBotIsChannelAdmin("@" + cleaned);
            if (problem != null) {
                sendText(chatId, "❌ @" + cleaned + " qo'shilmadi: " + problem
                        + "\n\nBotni kanalga admin qilib, qaytadan yuboring:");
                return;
            }
            if (requiredChannelService.exists(cleaned, null)) {
                sendText(chatId, "Bu kanal allaqachon ro'yxatda. Boshqa kanal yuboring:");
                return;
            }

            pending.setChannelUsername(cleaned);
            pending.setChannelChatId(null);
            pending.setChannelInviteLink(null);
            pending.setChannelJoinRequest(false);
            pending.setChannelTitle(fetchChatTitle("@" + cleaned));
        }

        sessionManager.setState(userId, AdminState.AWAITING_CHANNEL_DURATION);
        String name = pending.getChannelTitle() != null ? pending.getChannelTitle()
                : (pending.getChannelUsername() != null ? "@" + pending.getChannelUsername() : pending.getChannelChatId());
        sendTextWithMarkup(chatId, "\"" + name + "\" uchun majburiy obuna muddati qancha?\n\nTugmani tanlang yoki kunlar sonini yozing (masalan 14):",
                Keyboards.channelDurationMenu());
    }

    // days = 0 bo'lsa muddatsiz
    private void finishAddChannel(long chatId, long userId, int days) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);

        if (pending.getChannelUsername() == null && pending.getChannelChatId() == null) {
            sessionManager.clear(userId);
            sendText(chatId, "Jarayon topilmadi, kanalni qaytadan qo'shing.");
            sendChannelList(chatId);
            return;
        }

        LocalDateTime expiresAt = days == 0 ? null : LocalDateTime.now().plusDays(days);
        RequiredChannel channel = requiredChannelService.add(
                pending.getChannelUsername(),
                pending.getChannelChatId(),
                pending.getChannelTitle(),
                pending.getChannelInviteLink(),
                pending.isChannelJoinRequest(),
                expiresAt);

        sessionManager.clear(userId);
        sendText(chatId, "✅ " + channel.displayName() + " qo'shildi. "
                + (expiresAt == null ? "Muddatsiz." : "Muddat: " + expiresAt.format(DATE_TIME) + " gacha."));
        sendChannelList(chatId);
    }

    // Kanalga bot ko'rsatganidan keyin qo'shilganlarni sanash uchun: kimga qaysi kanallar ko'rsatildi
    private final Map<Long, Set<Long>> promptedChannels = new ConcurrentHashMap<>();

    private List<RequiredChannel> getUnjoinedChannels(long userId) {
        List<RequiredChannel> channels = requiredChannelService.getActiveChannels();
        List<RequiredChannel> unjoined = new ArrayList<>();
        Set<Long> prompted = promptedChannels.getOrDefault(userId, Set.of());

        for (RequiredChannel channel : channels) {
            if (!isMemberOf(channel, userId)) {
                unjoined.add(channel);
            } else if (prompted.contains(channel.getId())) {
                // bot so'raganidan keyin qo'shildi
                requiredChannelService.recordJoin(channel.getId(), userId, false);
            }
        }

        if (unjoined.isEmpty()) {
            promptedChannels.remove(userId);
        } else {
            Set<Long> ids = new HashSet<>();
            unjoined.forEach(c -> ids.add(c.getId()));
            promptedChannels.put(userId, ids);
        }

        return unjoined;
    }

    // Zayavkali (yopiq) kanalga zayavka kelganda: shu foydalanuvchi obuna bo'lgan hisoblanadi
    private void handleJoinRequest(ChatJoinRequest request) {
        if (request.getChat() == null || request.getUser() == null) return;

        String channelChatId = String.valueOf(request.getChat().getId());
        requiredChannelService.findByChatId(channelChatId)
                .filter(RequiredChannel::isJoinRequest)
                .ifPresent(channel -> requiredChannelService.recordJoin(channel.getId(), request.getUser().getId(), true));
    }

    // Muddati tugagan kanal haqida va boshqa tizim xabarlari uchun
    public void notifyAdmins(String text) {
        for (User admin : userService.getAllAdmins()) {
            sendText(admin.getUserId(), text);
        }
    }

    private boolean ensureSubscribed(long chatId, long userId) {
        if (userService.isAdmin(userId)) {
            return true;
        }

        List<RequiredChannel> unjoined = getUnjoinedChannels(userId);

        if (unjoined.isEmpty()) {
            return true;
        }

        SendMessage message = new SendMessage(String.valueOf(chatId), t(chatId, "sub.prompt"));
        message.setReplyMarkup(Keyboards.subscriptionPromptMenu(unjoined, tr(chatId)));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi", e);
        }

        return false;
    }

    private void captureBroadcastContent(long chatId, long userId, Message message) {
        if (message == null) {
            sendText(chatId, "Iltimos, matn, rasm yoki video yuboring.");
            return;
        }

        PendingMovie pending = sessionManager.getPendingMovie(userId);
        pending.setBroadcastSourceChatId(chatId);
        pending.setBroadcastSourceMessageId(message.getMessageId());
        pending.setBroadcastButtonText(null);
        pending.setBroadcastButtonUrl(null);
        sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_BUTTON);

        sendTextWithMarkup(chatId, "Reklama ostiga havola tugmasi qo'shasizmi?\n\n"
                        + "Qo'shish uchun havolani yuboring, masalan:\n\n" + BROADCAST_BUTTON_EXAMPLES,
                Keyboards.broadcastButtonMenu());
    }

    // Reklama tayyor: adminga qanday ko'rinishini ko'rsatib, tasdiq so'raymiz
    private void showBroadcastPreview(long chatId, long userId) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        if (pending.getBroadcastSourceMessageId() == null) {
            sessionManager.clear(userId);
            sendText(chatId, "Reklama xabari topilmadi. Qaytadan boshlang.");
            sendAdminMenu(chatId);
            return;
        }

        try {
            CopyMessage preview = new CopyMessage();
            preview.setChatId(String.valueOf(chatId));
            preview.setFromChatId(String.valueOf(pending.getBroadcastSourceChatId()));
            preview.setMessageId(pending.getBroadcastSourceMessageId());
            if (pending.getBroadcastButtonUrl() != null) {
                preview.setReplyMarkup(Keyboards.linkButton(pending.getBroadcastButtonText(), pending.getBroadcastButtonUrl()));
            }
            execute(preview);
        } catch (TelegramApiException e) {
            log.error("Reklama ko'rinishini yuborib bo'lmadi", e);
        }

        sessionManager.setState(userId, AdminState.AWAITING_BROADCAST_CONFIRM);
        int count = userService.getAllActiveUserIds().size();
        sendTextWithMarkup(chatId, "☝️ Reklama shunday ko'rinadi. U " + count + " ta foydalanuvchiga yuboriladi.\n\n"
                        + "⚠️ Yuborilib bo'lguncha asl xabarni o'chirmang — reklama undan nusxalanadi.",
                Keyboards.broadcastConfirmMenu());
    }

    private void scheduleBroadcast(long chatId, long userId, LocalDateTime sendAt) {
        PendingMovie pending = sessionManager.getPendingMovie(userId);
        if (pending.getBroadcastSourceMessageId() == null) {
            sessionManager.clear(userId);
            sendText(chatId, "Reklama xabari topilmadi. Qaytadan boshlang.");
            sendAdminMenu(chatId);
            return;
        }

        Broadcast broadcast = broadcastService.schedule(userId,
                pending.getBroadcastSourceChatId(),
                pending.getBroadcastSourceMessageId(),
                pending.getBroadcastButtonText(),
                pending.getBroadcastButtonUrl(),
                sendAt);
        sessionManager.clear(userId);

        boolean now = !sendAt.isAfter(LocalDateTime.now());
        sendText(chatId, now
                ? "✅ Reklama #" + broadcast.getId() + " navbatga qo'yildi, bir necha soniyada yuborila boshlaydi."
                : "✅ Reklama #" + broadcast.getId() + " " + sendAt.format(DATE_TIME) + " da yuboriladi.\n"
                + "Bekor qilish: 📣 Reklama → 🗓 Rejalashtirilganlar.");
        sendAdminMenu(chatId);
    }

    private void sendScheduledBroadcasts(long chatId) {
        List<Broadcast> scheduled = broadcastService.findScheduled();
        StringBuilder text = new StringBuilder("🗓 <b>Rejalashtirilgan reklamalar</b>\n\n");

        if (scheduled.isEmpty()) {
            text.append("Hozircha yo'q.");
        } else {
            for (Broadcast b : scheduled) {
                text.append("#").append(b.getId()).append(" · ").append(b.getSendAt().format(DATE_TIME));
                if (b.getButtonText() != null) {
                    text.append(" · 🔘 ").append(escape(b.getButtonText()));
                }
                text.append("\n");
            }
            text.append("\nBekor qilish uchun pastdagi tugmani bosing.");
        }

        sendHtml(chatId, text.toString(), Keyboards.scheduledBroadcastsMenu(scheduled, DATE_TIME));
    }

    private void sendBroadcastHistory(long chatId) {
        List<Broadcast> recent = broadcastService.findRecentFinished(10);
        StringBuilder text = new StringBuilder("📈 <b>Oxirgi reklamalar</b>\n\n");

        if (recent.isEmpty()) {
            text.append("Hozircha yuborilgan reklama yo'q.");
        }

        for (Broadcast b : recent) {
            LocalDateTime when = b.getStartedAt() != null ? b.getStartedAt() : b.getSendAt();
            text.append("#").append(b.getId()).append(" · ").append(when.format(DATE_TIME)).append(" · ")
                    .append(switch (b.getStatus()) {
                        case SENDING -> "⏳ yuborilmoqda";
                        case INTERRUPTED -> "⚠️ to'xtab qolgan";
                        default -> "✅ yakunlangan";
                    })
                    .append("\n    ✅ ").append(b.getSent())
                    .append("  🚫 ").append(b.getBlocked())
                    .append("  ❌ ").append(b.getFailed())
                    .append("  (jami ").append(b.getTotal()).append(")\n\n");
        }

        sendHtml(chatId, text.toString(), Keyboards.backToBroadcastMenu());
    }

    // Boshqa komponentlar (reklama yuboruvchi va h.k.) adminga xabar yuborishi uchun
    public void sendNotice(long chatId, String text) {
        sendText(chatId, text);
    }


}