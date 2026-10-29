package com.moviebot.bot.service;

import com.moviebot.bot.domain.ChannelJoin;
import com.moviebot.bot.domain.RequiredChannel;
import com.moviebot.bot.repo.ChannelJoinRepository;
import com.moviebot.bot.repo.RequiredChannelRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RequiredChannelServiceTest {

    private final RequiredChannelRepository channels = mock(RequiredChannelRepository.class);
    private final ChannelJoinRepository joins = mock(ChannelJoinRepository.class);
    private final RequiredChannelService service = new RequiredChannelService(channels, joins);

    @Test
    void sameUserIsCountedOnlyOnce() {
        when(joins.existsByChannelIdAndUserId(1L, 7L)).thenReturn(false, true);

        assertThat(service.recordJoin(1L, 7L, false)).isTrue();
        assertThat(service.recordJoin(1L, 7L, false)).isFalse();
        verify(joins, times(1)).save(any());
    }

    @Test
    void joinRequestIsStoredAsRequest() {
        service.recordJoin(1L, 7L, true);

        ArgumentCaptor<ChannelJoin> saved = ArgumentCaptor.forClass(ChannelJoin.class);
        verify(joins).save(saved.capture());
        assertThat(saved.getValue().isViaRequest()).isTrue();
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void activeChannelsExcludeExpiredOnes() {
        service.getActiveChannels();

        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(channels).findEnforced(now.capture());
        assertThat(now.getValue()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
    }

    @Test
    void existsChecksBothUsernameAndChatId() {
        when(channels.findByChannelUsernameIgnoreCase("kanal")).thenReturn(Optional.of(new RequiredChannel()));
        when(channels.findByChatId("-1001")).thenReturn(Optional.empty());

        assertThat(service.exists("kanal", null)).isTrue();
        assertThat(service.exists(null, "-1001")).isFalse();
    }

    @Test
    void publicChannelUsesUsername() {
        RequiredChannel c = RequiredChannel.builder().channelUsername("mykanal").build();

        assertThat(c.chatIdentifier()).isEqualTo("@mykanal");
        assertThat(c.url()).isEqualTo("https://t.me/mykanal");
        assertThat(c.displayName()).isEqualTo("@mykanal");
    }

    @Test
    void privateChannelUsesChatIdAndInviteLink() {
        RequiredChannel c = RequiredChannel.builder()
                .chatId("-1001234567890")
                .inviteLink("https://t.me/+abc")
                .title("Yopiq kanal")
                .joinRequest(true)
                .build();

        assertThat(c.chatIdentifier()).isEqualTo("-1001234567890");
        assertThat(c.url()).isEqualTo("https://t.me/+abc");
        assertThat(c.displayName()).isEqualTo("Yopiq kanal");
    }
}
