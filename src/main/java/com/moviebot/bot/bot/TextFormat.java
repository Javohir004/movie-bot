package com.moviebot.bot.bot;

public final class TextFormat {

    private TextFormat() {
    }

    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String formatCount(int count) {
        if (count >= 1000) {
            return String.format("%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    // Tavsifni (escape qilingan holda) available belgiga sig'adigan qilib qisqartiradi.
    // Teglar va &amp; ham hisobga kiradi, shuning uchun hisob har doim zaxira bilan chiqadi.
    public static String fitDescription(String description, int available) {
        if (description == null) return "";
        if (available <= 0) return "";

        String text = description;
        if (escape(text).length() > available) {
            text = text.substring(0, Math.max(0, Math.min(text.length(), available) - 1));
            while (!text.isEmpty() && escape(text).length() > available - 1) {
                text = text.substring(0, text.length() - 1);
            }
            // emoji (surrogate pair) o'rtasidan kesilib qolmasin
            if (!text.isEmpty() && Character.isHighSurrogate(text.charAt(text.length() - 1))) {
                text = text.substring(0, text.length() - 1);
            }
            text = text + "…";
        }
        return escape(text);
    }
}
