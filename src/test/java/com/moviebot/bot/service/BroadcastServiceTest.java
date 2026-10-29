package com.moviebot.bot.service;

import com.moviebot.bot.domain.Broadcast;
import com.moviebot.bot.enums.BroadcastStatus;
import com.moviebot.bot.repo.BroadcastRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BroadcastServiceTest {

    private final BroadcastRepository repository = mock(BroadcastRepository.class);
    private final BroadcastService service = new BroadcastService(repository);

    @Test
    void newBroadcastIsScheduled() {
        LocalDateTime at = LocalDateTime.now().plusHours(2);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Broadcast b = service.schedule(1L, 1L, 55, "Obuna", "https://t.me/x", at);

        assertThat(b.getStatus()).isEqualTo(BroadcastStatus.SCHEDULED);
        assertThat(b.getSendAt()).isEqualTo(at);
        assertThat(b.getCreatedAt()).isNotNull();
    }

    @Test
    void cancelOnlyTouchesScheduledBroadcasts() {
        when(repository.changeStatusIf(5L, BroadcastStatus.SCHEDULED, BroadcastStatus.CANCELLED)).thenReturn(0);

        assertThat(service.cancel(5L)).isFalse();
    }

    @Test
    void startingTwiceIsPrevented() {
        when(repository.startIfStatus(eq(5L), eq(BroadcastStatus.SCHEDULED), eq(BroadcastStatus.SENDING), any(), eq(100)))
                .thenReturn(1, 0);

        assertThat(service.markSending(5L, 100)).isTrue();
        assertThat(service.markSending(5L, 100)).isFalse();
    }

    @Test
    void finishMarksDone() {
        service.finish(5L, 90, 8, 2);

        ArgumentCaptor<BroadcastStatus> status = ArgumentCaptor.forClass(BroadcastStatus.class);
        verify(repository).finish(eq(5L), status.capture(), eq(90), eq(8), eq(2), any());
        assertThat(status.getValue()).isEqualTo(BroadcastStatus.DONE);
    }
}
