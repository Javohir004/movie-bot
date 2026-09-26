package com.moviebot.bot;

import com.moviebot.bot.domain.Keyboards;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.service.MovieService;
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


    public MovieBot(@Value("${bot.token}") String token,
                    @Value("${bot.username}") String username,
                    MovieService movieService, UserService userService) {
        super(token);
        this.username = username;
        this.movieService = movieService;
        this.userService = userService;
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

            if (data.startsWith("MOVIE:")) {
                String code = data.substring("MOVIE:".length());
                sendMovieByCode(chatId, code);
                return;
            }

            if (data.startsWith("ADMIN:") && userService.isAdmin(userId)) {
                handleAdminCallback(chatId, userId, data);
                return;
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
                    sendText(chatId, "Salom 👋 , " + firstName + " ! Xush kelibsiz 😆😁.");
                    sendAdminMenu(chatId);
                } else {
                    sendText(chatId, "Salom! Men kino va anime botiman.\n\nKino nomini yoki kodini yozing, men qidirib topaman.");
                }
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

    private void sendAdminMenu(long chatId) {
        SendMessage message = new SendMessage(String.valueOf(chatId), "Admin panel:");
        message.setReplyMarkup(Keyboards.adminMenu());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void handleAdminCallback(long chatId, long userId, String data) {
        switch (data) {
            case "ADMIN:ADD_MOVIE" -> sendText(chatId, "Kino qo'shish hali tayyor emas (keyingi qadamda qo'shamiz).");
            case "ADMIN:ADD_ADMIN" -> sendText(chatId, "Yozing: /addadmin <telegram_id>");
            default -> sendText(chatId, "Noma'lum amal.");
        }
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
            sendText(chatId, movie.getTitle() + "\n\n" + movie.getDescription() + "\n\n(Video hali yuklanmagan)");
            return;
        }

        SendVideo video = new SendVideo();
        video.setChatId(String.valueOf(chatId));
        video.setVideo(new InputFile(movie.getFileId()));
        video.setCaption(movie.getTitle() + "\n\n" + movie.getDescription());

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
}