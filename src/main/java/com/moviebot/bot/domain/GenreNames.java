package com.moviebot.bot.domain;

// Janr nomini foydalanuvchi tiliga qarab tanlaydi (Genre va so'rov natijalari uchun umumiy)
public final class GenreNames {

    private GenreNames() {
    }

    public static String pick(String name, String uz, String ru, String en, String language) {
        String translated = switch (language == null ? "" : language) {
            case "uz" -> uz;
            case "ru" -> ru;
            case "en" -> en;
            default -> null;
        };
        return translated == null || translated.isBlank() ? name : translated;
    }
}
