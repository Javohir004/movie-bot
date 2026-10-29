package com.moviebot.bot.service;

import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.MovieRepository;
import com.moviebot.bot.repo.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StatsServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final MovieRepository movies = mock(MovieRepository.class);
    private final StatsService service = new StatsService(users, movies);

    @Test
    void blockedUsersAreTotalMinusReachable() {
        when(users.count()).thenReturn(100L);
        when(users.countByActiveTrue()).thenReturn(80L);

        assertThat(service.collect().blockedUsers()).isEqualTo(20);
    }

    @Test
    void todayStartsAtMidnight() {
        service.collect();

        verify(users).countByCreatedAtGreaterThanEqual(LocalDate.now().atStartOfDay());
        verify(users).countByLastSeenAtGreaterThanEqual(LocalDate.now().atStartOfDay());
    }

    @Test
    void viewsCombineMoviesAndSeasons() {
        when(movies.sumMovieViews()).thenReturn(40L);
        when(movies.sumSeasonViews()).thenReturn(2L);

        assertThat(service.collect().totalViews()).isEqualTo(42);
    }

    @Test
    void reportContainsNumbers() {
        when(users.count()).thenReturn(12345L);
        when(users.countByRole(Role.ADMIN)).thenReturn(3L);
        when(movies.countByTypeAndActiveTrue(MovieType.ANIME)).thenReturn(7L);
        when(users.countByCreatedAtGreaterThanEqual(any())).thenReturn(0L);

        String report = service.buildReport();

        assertThat(report).contains("Jami: 12345", "Adminlar: 3", "Anime: 7");
    }
}
