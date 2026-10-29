package com.moviebot.bot.bot;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastInputTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 15, 30);

    @Test
    void timeLaterTodayStaysToday() {
        assertThat(BroadcastInput.parseTime("20:00", NOW)).contains(LocalDateTime.of(2026, 10, 29, 20, 0));
    }

    @Test
    void timeAlreadyPassedMovesToTomorrow() {
        assertThat(BroadcastInput.parseTime("9:05", NOW)).contains(LocalDateTime.of(2026, 10, 30, 9, 5));
    }

    @Test
    void dayMonthUsesCurrentYear() {
        assertThat(BroadcastInput.parseTime("31.10 08:00", NOW)).contains(LocalDateTime.of(2026, 10, 31, 8, 0));
    }

    @Test
    void fullDateIsAccepted() {
        assertThat(BroadcastInput.parseTime(" 01.11.2026 21:15 ", NOW)).contains(LocalDateTime.of(2026, 11, 1, 21, 15));
    }

    @Test
    void pastDateIsRejected() {
        assertThat(BroadcastInput.parseTime("28.10.2026 20:00", NOW)).isEmpty();
        assertThat(BroadcastInput.parseTime("29.10 15:00", NOW)).isEmpty();
    }

    @Test
    void tooFarAheadIsRejected() {
        assertThat(BroadcastInput.parseTime("01.06.2027 10:00", NOW)).isEmpty();
    }

    @Test
    void invalidValuesAreRejected() {
        assertThat(BroadcastInput.parseTime("25:00", NOW)).isEmpty();
        assertThat(BroadcastInput.parseTime("31.02 10:00", NOW)).isEmpty();
        assertThat(BroadcastInput.parseTime("ertaga", NOW)).isEmpty();
        assertThat(BroadcastInput.parseTime(null, NOW)).isEmpty();
    }

    @Test
    void buttonWithHttpsLink() {
        assertThat(BroadcastInput.parseButton("Kanalga o'tish | https://t.me/mykanal"))
                .contains(new BroadcastInput.LinkButton("Kanalga o'tish", "https://t.me/mykanal"));
    }

    @Test
    void buttonWithShortTelegramForms() {
        assertThat(BroadcastInput.parseButton("Obuna | t.me/kanal")).map(BroadcastInput.LinkButton::url).contains("https://t.me/kanal");
        assertThat(BroadcastInput.parseButton("Obuna | @kanal")).map(BroadcastInput.LinkButton::url).contains("https://t.me/kanal");
    }

    @Test
    void invalidButtonsAreRejected() {
        assertThat(BroadcastInput.parseButton("faqat matn")).isEmpty();
        assertThat(BroadcastInput.parseButton(" | https://t.me/x")).isEmpty();
        assertThat(BroadcastInput.parseButton("Matn | kanal.uz")).isEmpty();
        assertThat(BroadcastInput.parseButton("x".repeat(65) + " | https://t.me/x")).isEmpty();
    }
}
