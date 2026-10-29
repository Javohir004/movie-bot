package com.moviebot.bot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.Serializable;

// Telegram'ga so'rov yuboruvchi yagona joy. MovieBot faqat update qabul qiladi;
// handler'lar, reklama yuboruvchi va boshqalar xabarni shu orqali yuboradi
// (aks holda ular MovieBot'ga, MovieBot esa ularga bog'liq bo'lib, aylana hosil bo'lardi).
@Slf4j
@Component
public class TelegramSender extends DefaultAbsSender {

    public TelegramSender(@Value("${bot.token}") String token) {
        super(new DefaultBotOptions(), token);
    }

    // xato bo'lsa logga yozadi va null qaytaradi (javob kerak bo'lmagan yuborishlar uchun)
    public <T extends Serializable, M extends BotApiMethod<T>> T send(M method) {
        try {
            return execute(method);
        } catch (TelegramApiException e) {
            log.error("Telegram so'rovi bajarilmadi: {}", method.getMethod(), e);
            return null;
        }
    }

    public void send(SendPhoto photo) {
        try {
            execute(photo);
        } catch (TelegramApiException e) {
            log.error("Rasm yuborilmadi", e);
        }
    }

    public void send(SendVideo video) {
        try {
            execute(video);
        } catch (TelegramApiException e) {
            log.error("Video yuborilmadi", e);
        }
    }

    public void sendText(long chatId, String text) {
        send(new SendMessage(String.valueOf(chatId), text));
    }

    public void sendText(long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), text);
        message.setReplyMarkup(markup);
        send(message);
    }

    public void sendHtml(long chatId, String html, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(String.valueOf(chatId), html);
        message.setParseMode("HTML");
        message.setReplyMarkup(markup);
        send(message);
    }
}
