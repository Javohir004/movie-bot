package com.moviebot.bot.bot;

import com.moviebot.bot.domain.Episode;
import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.enums.MovieType;
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

    public static InlineKeyboardMarkup movieDetailMenu(Movie movie) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (movie.getType() != MovieType.MOVIE) {
            rows.add(List.of(button("➕ Yangi fasl qo'shish", "ADDSEASON:" + movie.getCode())));
            rows.add(List.of(button("📺 Fasllarni boshqarish", "ADMIN_SEASONS:" + movie.getCode())));
        }

        rows.add(List.of(
                button("✏️ Tahrirlash", "EDIT:" + movie.getCode()),
                button("🗑 O'chirish", "DELETE:" + movie.getCode())
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

    public static InlineKeyboardMarkup userMainMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🔍 Qidirish", "USER:SEARCH")));
        rows.add(List.of(button("📽 Barchasini ko'rish", "USER:LIST_ALL")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup userMovieListMenu(List<Movie> movies) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Movie m : movies) {
            rows.add(List.of(button(m.getCode() + " — " + m.getTitle(), "MOVIE:" + m.getCode())));
        }
        rows.add(List.of(button("🔙 Orqaga", "USER:MENU")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup backToUserMenu() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🔙 Orqaga", "USER:MENU")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup moviePosterMenu(String code) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("▶️ Video ko'rish", "WATCHMOVIE:" + code)));
        rows.add(List.of(button("🔙 Orqaga", "USER:MENU")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup seasonListMenu(List<Season> seasons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Season s : seasons) {
            rows.add(List.of(button(s.getSeasonNumber() + "-fasl", "SEASON:" + s.getId())));
        }
        rows.add(List.of(button("🔙 Orqaga", "USER:MENU")));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup episodeGridMenu(List<Episode> episodes, String movieCode) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> currentRow = new ArrayList<>();

        int perRow = 5;
        for (Episode e : episodes) {
            currentRow.add(button(String.valueOf(e.getEpisodeNumber()), "EPISODE:" + e.getId()));
            if (currentRow.size() == perRow) {
                rows.add(currentRow);
                currentRow = new ArrayList<>();
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        rows.add(List.of(button("🔙 Fasllarga", "SERIES:" + movieCode)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup backToSeasonMenu(Long seasonId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(button("🔙 Qismlarga", "SEASON:" + seasonId)));
        return new InlineKeyboardMarkup(rows);
    }

    private static InlineKeyboardButton button(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    public static InlineKeyboardMarkup seasonManageListMenu(List<Season> seasons, String movieCode) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Season s : seasons) {
            rows.add(List.of(button(s.getSeasonNumber() + "-fasl", "SEASONMANAGE:" + s.getId())));
        }

        rows.add(List.of(button("🔙 Orqaga", "LISTITEM:" + movieCode)));
        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup seasonManageMenu(Long seasonId, String movieCode) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        rows.add(List.of(button("🖼 Poster almashtirish", "SEASONPOSTER:" + seasonId)));
        rows.add(List.of(button("➕ Qism qo'shish", "ADDEPISODE:" + seasonId)));
        rows.add(List.of(button("🔙 Orqaga", "ADMIN_SEASONS:" + movieCode)));

        return new InlineKeyboardMarkup(rows);
    }

    public static InlineKeyboardMarkup episodeVideoMenu(Long seasonId, Long nextEpisodeId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (nextEpisodeId != null) {
            rows.add(List.of(button("▶️ Keyingi qism", "EPISODE:" + nextEpisodeId)));
        }

        rows.add(List.of(button("🔙 Qismlarga", "SEASON:" + seasonId)));
        return new InlineKeyboardMarkup(rows);
    }
}