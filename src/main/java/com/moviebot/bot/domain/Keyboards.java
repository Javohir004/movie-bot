package com.moviebot.bot.domain;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

public class Keyboards {

    public static InlineKeyboardMarkup adminMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(button("🎬 Kino/serial qo'shish", "ADMIN:ADD_MOVIE")));
        rows.add(List.of(button("📋 Ro'yxat", "ADMIN:LIST_MOVIES")));
        rows.add(List.of(button("👤 Admin qo'shish", "ADMIN:ADD_ADMIN")));

        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup movieTypeMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                button("🎬 Kino", "TYPE:MOVIE"),
                button("📺 Serial", "TYPE:SERIES")
        ));
        rows.add(List.of(button("🇯🇵 Anime", "TYPE:ANIME")));
        rows.add(List.of(button("🔙 Orqaga", "BACK")));

        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup backOnly() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🔙 Orqaga", "BACK")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup movieListMenu(List<Movie> movies) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Movie m : movies) {
            rows.add(List.of(button(m.getCode() + " — " + m.getTitle(), "LISTITEM:" + m.getCode())));
        }
        rows.add(List.of(button("🔙 Menyuga", "ADMIN:MENU")));

        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup movieDetailMenu(String code) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(
                button("✏️ Tahrirlash", "EDIT:" + code),
                button("🗑 O'chirish", "DELETE:" + code)
        ));
        rows.add(List.of(button("🔙 Ro'yxatga", "ADMIN:LIST_MOVIES")));

        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup deleteConfirmMenu(String code) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(
                button("✅ Ha, o'chir", "DELCONF:" + code),
                button("❌ Bekor qilish", "LISTITEM:" + code)
        ));
        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
}