package com.moviebot.bot.domain;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup adminMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(button("🎬 Kino/serial qo'shish", "ADMIN:ADD_MOVIE")));
        rows.add(List.of(button("👤 Admin qo'shish", "ADMIN:ADD_ADMIN")));

        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}
