package com.moviebot.bot.bot;

import com.moviebot.bot.bot.handler.*;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

// Telegram'dan update qabul qiladi va kerakli handler'ga yo'naltiradi.
// Mantiqning o'zi handler paketida: UserHandler (foydalanuvchi), Admin*Handler (admin panel bo'limlari).
@Slf4j
@Component
public class MovieBot extends TelegramLongPollingBot {

    private final String username;
    private final TelegramSender sender;
    private final UserService userService;
    private final AdminSessionManager sessionManager;
    private final SubscriptionGate gate;
    private final UserHandler userHandler;
    private final AdminPanelHandler panel;
    private final AdminMovieHandler movieHandler;
    private final AdminChannelHandler channelHandler;
    private final AdminBroadcastHandler broadcastHandler;
    private final AdminGenreHandler genreHandler;

    public MovieBot(@Value("${bot.token}") String token,
                    @Value("${bot.username}") String username,
                    TelegramSender sender,
                    UserService userService,
                    AdminSessionManager sessionManager,
                    SubscriptionGate gate,
                    UserHandler userHandler,
                    AdminPanelHandler panel,
                    AdminMovieHandler movieHandler,
                    AdminChannelHandler channelHandler,
                    AdminBroadcastHandler broadcastHandler,
                    AdminGenreHandler genreHandler) {
        super(token);
        this.username = username;
        this.sender = sender;
        this.userService = userService;
        this.sessionManager = sessionManager;
        this.gate = gate;
        this.userHandler = userHandler;
        this.panel = panel;
        this.movieHandler = movieHandler;
        this.channelHandler = channelHandler;
        this.broadcastHandler = broadcastHandler;
        this.genreHandler = genreHandler;
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
            gate.handleJoinRequest(update.getChatJoinRequest());
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update);
            return;
        }

        if (!update.hasMessage() || update.getMessage().getFrom() == null) return;

        Message message = update.getMessage();
        long userId = message.getFrom().getId();
        long chatId = message.getChatId();

        // Reklama kutilayotganda istalgan turdagi xabar (matn, rasm, video, GIF, fayl...) qabul qilinadi.
        // Buyruqlar (/start va h.k.) bundan mustasno.
        boolean command = message.hasText() && message.getText().startsWith("/");
        if (!command && sessionManager.getState(userId) == AdminState.AWAITING_BROADCAST_CONTENT
                && userService.isAdmin(userId)) {
            broadcastHandler.captureContent(chatId, userId, message);
            return;
        }

        if (message.hasPhoto()) {
            if (userService.isAdmin(userId)) {
                List<PhotoSize> photos = message.getPhoto();
                String fileId = photos.get(photos.size() - 1).getFileId();
                movieHandler.handlePhoto(chatId, userId, sessionManager.getState(userId), fileId);
            }
            return;
        }

        if (message.hasVideo()) {
            if (userService.isAdmin(userId)) {
                movieHandler.handleVideo(chatId, userId, sessionManager.getState(userId), message.getVideo().getFileId());
            }
            return;
        }

        if (message.hasText()) {
            handleText(chatId, userId, message);
        }
    }

    private void handleCallback(Update update) {
        if (update.getCallbackQuery().getFrom() == null) return;

        sender.answerCallback(update.getCallbackQuery().getId());

        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        // Tartib muhim: foydalanuvchi tugmalari (obuna tekshiruvi bilan) -> taklif havolasi -> admin tugmalari
        if (userHandler.handleCallback(chatId, userId, messageId, data)) return;
        if (panel.handleInviteCallback(chatId, userId, data)) return;

        if (userService.isAdmin(userId)) {
            handleAdminCallback(chatId, userId, messageId, data);
        }
    }

    // Admin tugmalarining prefikslari bir-biri bilan kesishmaydi, shuning uchun handler'lar tartibi natijaga ta'sir qilmaydi
    private void handleAdminCallback(long chatId, long userId, Integer messageId, String data) {
        if (movieHandler.handleCallback(chatId, userId, messageId, data)) return;
        if (panel.handleCallback(chatId, userId, data)) return;
        if (channelHandler.handleCallback(chatId, userId, data)) return;
        if (broadcastHandler.handleCallback(chatId, userId, data)) return;
        if (genreHandler.handleCallback(chatId, userId, messageId, data)) return;

        sender.sendText(chatId, "Noma'lum amal.");
    }

    private void handleText(long chatId, long userId, Message message) {
        String text = message.getText();
        String firstName = message.getFrom().getFirstName();
        userService.registerOrUpdateName(userId, firstName);

        if (text.startsWith("/start")) {
            String[] startParts = text.trim().split("\\s+", 2);

            if (startParts.length > 1) {
                panel.handleInviteStart(chatId, userId, firstName, startParts[1]);
                return;
            }

            if (userService.isAdmin(userId)) {
                sender.sendText(chatId, "Salom, " + firstName + "! Xush kelibsiz.");
                panel.showMenu(chatId);
            } else {
                userHandler.handleStart(chatId, userId);
            }
            return;
        }

        if (userService.isAdmin(userId) && sessionManager.getState(userId) != AdminState.NONE) {
            handleAdminFlowInput(chatId, userId, text);
            return;
        }

        if (text.startsWith("/") && userService.isAdmin(userId)) {
            sender.sendText(chatId, "Noma'lum admin buyrug'i. Admin panel orqali foydalaning.");
            return;
        }

        if (text.startsWith("/")) {
            userHandler.sendUnknownCommand(chatId);
            return;
        }

        if (!userService.isAdmin(userId) && !gate.ensureSubscribed(chatId, userId)) {
            return;
        }

        userHandler.handleSearch(chatId, text);
    }

    // Admin biror jarayon o'rtasida matn yozdi: holatga qarab tegishli handler'ga
    private void handleAdminFlowInput(long chatId, long userId, String text) {
        AdminState state = sessionManager.getState(userId);

        if (movieHandler.handleText(chatId, userId, state, text)) return;
        if (channelHandler.handleText(chatId, userId, state, text)) return;
        if (genreHandler.handleText(chatId, userId, state, text)) return;
        if (broadcastHandler.handleText(chatId, userId, state, text)) return;

        sender.sendText(chatId, "Nimadir xato ketdi. Qaytadan /start bosing.");
    }
}
