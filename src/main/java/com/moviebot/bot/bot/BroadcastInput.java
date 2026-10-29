package com.moviebot.bot.bot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Admin yozgan reklama tugmasi va yuborish vaqtini tahlil qiladi
public final class BroadcastInput {

    public static final int MAX_DAYS_AHEAD = 90;

    private static final Pattern TIME_ONLY = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern DAY_MONTH_TIME = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern FULL = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern URL = Pattern.compile("(https?|tg)://\\S+");

    private BroadcastInput() {
    }

    public record LinkButton(String text, String url) {
    }

    // "Matn | https://link" ko'rinishida
    public static Optional<LinkButton> parseButton(String input) {
        if (input == null) return Optional.empty();

        int separator = input.lastIndexOf('|');
        if (separator < 0) return Optional.empty();

        String text = input.substring(0, separator).trim();
        String url = input.substring(separator + 1).trim();

        if (url.startsWith("@")) {
            url = "https://t.me/" + url.substring(1);
        } else if (url.startsWith("t.me/")) {
            url = "https://" + url;
        }

        if (text.isEmpty() || text.length() > 64 || !URL.matcher(url).matches() || url.length() > 512) {
            return Optional.empty();
        }
        return Optional.of(new LinkButton(text, url));
    }

    // "20:00" (bugun, o'tib ketgan bo'lsa ertaga), "30.10 20:00" yoki "30.10.2026 20:00".
    // Faqat kelajakdagi va MAX_DAYS_AHEAD kun ichidagi vaqt qabul qilinadi.
    public static Optional<LocalDateTime> parseTime(String input, LocalDateTime now) {
        if (input == null) return Optional.empty();
        String value = input.trim();

        try {
            LocalDateTime result;
            Matcher m;

            if ((m = TIME_ONLY.matcher(value)).matches()) {
                result = LocalDateTime.of(now.toLocalDate(), time(m.group(1), m.group(2)));
                if (!result.isAfter(now)) {
                    result = result.plusDays(1);
                }
            } else if ((m = DAY_MONTH_TIME.matcher(value)).matches()) {
                result = LocalDateTime.of(
                        LocalDate.of(now.getYear(), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))),
                        time(m.group(3), m.group(4)));
            } else if ((m = FULL.matcher(value)).matches()) {
                result = LocalDateTime.of(
                        LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))),
                        time(m.group(4), m.group(5)));
            } else {
                return Optional.empty();
            }

            if (!result.isAfter(now) || result.isAfter(now.plusDays(MAX_DAYS_AHEAD))) {
                return Optional.empty();
            }
            return Optional.of(result);
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static LocalTime time(String hours, String minutes) {
        return LocalTime.of(Integer.parseInt(hours), Integer.parseInt(minutes));
    }
}
