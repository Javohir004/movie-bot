package com.moviebot.bot.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextFormatTest {

    @Test
    void shortDescriptionIsOnlyEscaped() {
        assertThat(TextFormat.fitDescription("Tom & Jerry <3", 100)).isEqualTo("Tom &amp; Jerry &lt;3");
    }

    @Test
    void nullDescriptionBecomesEmpty() {
        assertThat(TextFormat.fitDescription(null, 100)).isEmpty();
    }

    @Test
    void longDescriptionIsTruncatedWithinLimit() {
        String result = TextFormat.fitDescription("a&b<".repeat(2000), 500);

        assertThat(result.length()).isLessThanOrEqualTo(500);
        assertThat(result).endsWith("…");
    }

    @Test
    void truncationDoesNotSplitEscapedEntity() {
        String result = TextFormat.fitDescription("&".repeat(1000), 100);

        // "&amp;" bo'laklari to'liq qolishi kerak
        assertThat(result.replace("&amp;", "")).isEqualTo("…");
    }

    @Test
    void truncationDoesNotSplitEmoji() {
        String result = TextFormat.fitDescription("😀".repeat(1000), 101);
        char beforeEllipsis = result.charAt(result.length() - 2);

        assertThat(Character.isHighSurrogate(beforeEllipsis)).isFalse();
        assertThat(result.length()).isLessThanOrEqualTo(101);
    }

    @Test
    void noRoomMeansEmptyDescription() {
        assertThat(TextFormat.fitDescription("matn", 0)).isEmpty();
    }

    @Test
    void formatsLargeCounts() {
        assertThat(TextFormat.formatCount(999)).isEqualTo("999");
        assertThat(TextFormat.formatCount(1500)).isIn("1.5k", "1,5k");
    }
}
