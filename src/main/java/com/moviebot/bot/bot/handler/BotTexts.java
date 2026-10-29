package com.moviebot.bot.bot.handler;

import com.moviebot.bot.bot.I18n;
import com.moviebot.bot.service.UserService;
import org.springframework.stereotype.Component;

import java.util.function.Function;

// Foydalanuvchi tilida matn olish. Bot faqat shaxsiy chatda ishlaydi, shuning uchun chatId = userId.
@Component
public class BotTexts {

    private final UserService userService;
    private final I18n i18n;

    public BotTexts(UserService userService, I18n i18n) {
        this.userService = userService;
        this.i18n = i18n;
    }

    // tanlamagan bo'lsa o'zbekcha
    public String lang(long chatId) {
        return userService.findLanguage(chatId).orElse(I18n.DEFAULT_LANGUAGE);
    }

    public String t(long chatId, String key, Object... args) {
        return i18n.t(lang(chatId), key, args);
    }

    // Keyboards uchun: kalit -> matn
    public Function<String, String> tr(long chatId) {
        return i18n.forLanguage(lang(chatId));
    }
}
