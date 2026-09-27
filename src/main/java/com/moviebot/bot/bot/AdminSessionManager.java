package com.moviebot.bot.bot;

import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.enums.AdminState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AdminSessionManager {

    private final Map<Long, AdminState> states = new HashMap<>();
    private final Map<Long, PendingMovie> pendingMovies = new HashMap<>();

    public AdminState getState(Long userId) {
        return states.getOrDefault(userId, AdminState.NONE);
    }

    public void setState(Long userId, AdminState state) {
        states.put(userId, state);
    }

    public PendingMovie getPendingMovie(Long userId) {
        return pendingMovies.computeIfAbsent(userId, k -> new PendingMovie());
    }

    public void clear(Long userId) {
        states.remove(userId);
        pendingMovies.remove(userId);
    }
}
