package com.moviebot.bot.bot;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

// Foydalanuvchiga ko'rinadigan matnlar: messages.properties (uz), messages_ru, messages_en.
// MessageFormat ishlatilmaydi: u bitta apostrofni maxsus belgi deb tushunadi va o'zbekcha
// matnlarni ("Ko'rishlar") jimgina buzadi. {0}, {1} o'rinbosarlar shu yerda almashtiriladi.
@Component
public class I18n {

    public static final String DEFAULT_LANGUAGE = "uz";
    public static final List<String> SUPPORTED = List.of("uz", "ru", "en");

    private final MessageSource messages;

    public I18n(MessageSource messages) {
        this.messages = messages;
    }

    public String t(String language, String key, Object... args) {
        String text = messages.getMessage(key, null, key, Locale.forLanguageTag(normalize(language)));
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return text;
    }

    // Keyboards uchun: kalit -> matn
    public Function<String, String> forLanguage(String language) {
        return key -> t(language, key);
    }

    public static String normalize(String language) {
        return language != null && SUPPORTED.contains(language) ? language : DEFAULT_LANGUAGE;
    }
}
