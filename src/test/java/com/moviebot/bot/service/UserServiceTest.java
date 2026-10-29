package com.moviebot.bot.service;

import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private final UserRepository repository = mock(UserRepository.class);
    private final UserService service = new UserService(repository);

    @Test
    void userWhoWritesAgainIsReactivated() {
        User user = User.builder().userId(1L).firstName("Ali").role(Role.USER).active(false).build();
        when(repository.findByUserId(1L)).thenReturn(Optional.of(user));

        service.registerOrUpdateName(1L, "Ali");

        assertThat(user.isActive()).isTrue();
        verify(repository).save(user);
    }

    @Test
    void unchangedActiveUserIsNotSaved() {
        User user = User.builder().userId(1L).firstName("Ali").role(Role.USER).build();
        when(repository.findByUserId(1L)).thenReturn(Optional.of(user));

        service.registerOrUpdateName(1L, "Ali");

        verify(repository, never()).save(any());
    }

    @Test
    void broadcastTargetsOnlyActiveUsers() {
        when(repository.findByActiveTrue()).thenReturn(List.of(User.builder().userId(7L).build()));

        assertThat(service.getAllActiveUserIds()).containsExactly(7L);
        verify(repository, never()).findAll();
    }

    @Test
    void deactivateWorksInChunks() {
        List<Long> ids = LongStream.rangeClosed(1, 2500).boxed().toList();
        when(repository.findByUserIdIn(anyCollection())).thenReturn(List.of());

        service.deactivate(ids);

        verify(repository, times(3)).findByUserIdIn(anyCollection());
    }

    @Test
    void deactivateMarksUsersInactive() {
        User user = User.builder().userId(1L).build();
        when(repository.findByUserIdIn(anyCollection())).thenReturn(List.of(user));

        service.deactivate(List.of(1L));

        assertThat(user.isActive()).isFalse();
    }

    @Test
    void touchUpdatesLastSeenOnlyOncePerInterval() {
        User user = User.builder().userId(1L).active(false).build();
        when(repository.findByUserId(1L)).thenReturn(Optional.of(user));

        service.touch(1L);
        service.touch(1L);
        service.touch(1L);

        assertThat(user.getLastSeenAt()).isNotNull();
        assertThat(user.isActive()).isTrue();
        verify(repository, times(1)).findByUserId(1L);
    }

    @Test
    void deactivateWithNoIdsTouchesNothing() {
        service.deactivate(List.of());

        verifyNoInteractions(repository);
    }
}
