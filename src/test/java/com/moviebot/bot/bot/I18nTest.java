package com.moviebot.bot.bot;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTest {

    // application.properties dagi sozlamalar bilan bir xil
    private static I18n i18n() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return new I18n(source);
    }

    private static Properties load(String file) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream("/" + file)) {
            assertThat(in).as(file).isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    void everyKeyExistsInEveryLanguage() throws IOException {
        Set<String> uz = load("messages.properties").stringPropertyNames();
        Set<String> ru = load("messages_ru.properties").stringPropertyNames();
        Set<String> en = load("messages_en.properties").stringPropertyNames();

        assertThat(ru).as("ru").containsExactlyInAnyOrderElementsOf(uz);
        assertThat(en).as("en").containsExactlyInAnyOrderElementsOf(uz);
    }

    @Test
    void uzbekApostrophesAreNotLost() {
        // MessageFormat ishlatilsa "Ko'rishlar: {0}" -> "Korishlar: {0}" bo'lib buzilardi
        assertThat(i18n().t("uz", "caption.views", 12)).isEqualTo("👁 Ko'rishlar: 12");
    }

    @Test
    void placeholdersAreReplaced() {
        assertThat(i18n().t("ru", "episode.caption", 2, 7)).isEqualTo("2 сезон, 7 серия");
        assertThat(i18n().t("en", "episode.caption", 2, 7)).isEqualTo("Season 2, episode 7");
    }

    @Test
    void unknownOrMissingLanguageFallsBackToUzbek() {
        assertThat(i18n().t("de", "menu.search")).isEqualTo("🔍 Qidirish");
        assertThat(i18n().t(null, "menu.search")).isEqualTo("🔍 Qidirish");
    }

    @Test
    void multilineHtmlTextIsLoaded() {
        String start = i18n().t("en", "user.start");

        assertThat(start).startsWith("👋 Welcome!").contains("\n\n", "<b>Search</b>");
    }

    @Test
    void allNotFoundVariantsExist() {
        for (int i = 1; i <= 5; i++) {
            assertThat(i18n().t("ru", "notfound." + i)).doesNotStartWith("notfound.");
        }
    }
}
