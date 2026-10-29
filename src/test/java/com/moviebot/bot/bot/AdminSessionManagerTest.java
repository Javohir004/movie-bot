package com.moviebot.bot.bot;

import com.moviebot.bot.domain.AdminSession;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.repo.AdminSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminSessionManagerTest {

    private final AdminSessionRepository repository = mock(AdminSessionRepository.class);

    @Test
    void sessionSurvivesRestart() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        AdminSessionManager before = new AdminSessionManager(repository);
        before.setState(1L, AdminState.AWAITING_EPISODE_VIDEO);
        before.getPendingMovie(1L).setTitle("Naruto");
        before.getPendingMovie(1L).setType(MovieType.ANIME);
        before.getPendingMovie(1L).setCurrentEpisodeNumber(4);
        before.getPendingMovie(1L).setEditingCode("A1");

        before.persist(1L);

        ArgumentCaptor<AdminSession> saved = ArgumentCaptor.forClass(AdminSession.class);
        verify(repository).save(saved.capture());

        // "qayta ishga tushgan" bot
        when(repository.findById(1L)).thenReturn(Optional.of(saved.getValue()));
        AdminSessionManager after = new AdminSessionManager(repository);

        assertThat(after.getState(1L)).isEqualTo(AdminState.AWAITING_EPISODE_VIDEO);
        assertThat(after.getPendingMovie(1L).getTitle()).isEqualTo("Naruto");
        assertThat(after.getPendingMovie(1L).getType()).isEqualTo(MovieType.ANIME);
        assertThat(after.getPendingMovie(1L).getCurrentEpisodeNumber()).isEqualTo(4);
        assertThat(after.getPendingMovie(1L).isEditing()).isTrue();
    }

    @Test
    void oldSessionIsDiscarded() {
        AdminSession old = AdminSession.builder()
                .userId(1L)
                .state(AdminState.AWAITING_TITLE)
                .updatedAt(LocalDateTime.now().minusDays(3))
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(old));

        AdminSessionManager manager = new AdminSessionManager(repository);

        assertThat(manager.getState(1L)).isEqualTo(AdminState.NONE);
        verify(repository).delete(old);
    }

    @Test
    void brokenJsonIsDiscarded() {
        AdminSession broken = AdminSession.builder()
                .userId(1L)
                .state(AdminState.AWAITING_TITLE)
                .pendingJson("{not json")
                .updatedAt(LocalDateTime.now())
                .build();
        when(repository.findById(1L)).thenReturn(Optional.of(broken));

        AdminSessionManager manager = new AdminSessionManager(repository);

        assertThat(manager.getState(1L)).isEqualTo(AdminState.NONE);
        verify(repository).delete(broken);
    }

    @Test
    void clearRemovesSessionFromDatabase() {
        AdminSessionManager manager = new AdminSessionManager(repository);
        manager.setState(1L, AdminState.AWAITING_TITLE);

        manager.clear(1L);

        assertThat(manager.getState(1L)).isEqualTo(AdminState.NONE);
        verify(repository).deleteById(1L);
    }

    @Test
    void usersWithoutSessionCauseNoDatabaseWrites() {
        AdminSessionManager manager = new AdminSessionManager(repository);

        manager.persist(42L);

        verifyNoInteractions(repository);
    }
}
