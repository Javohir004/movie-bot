package com.moviebot.bot.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebot.bot.domain.AdminSession;
import com.moviebot.bot.domain.PendingMovie;
import com.moviebot.bot.enums.AdminState;
import com.moviebot.bot.repo.AdminSessionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Sessiyalar xotirada ishlaydi, lekin har bir update'dan keyin bazaga yoziladi,
// shuning uchun bot qayta ishga tushsa ham admin ishini davom ettira oladi
@Component
public class AdminSessionManager {

    // juda eski yarim qolgan sessiyalarni tiklamaymiz
    private static final int SESSION_TTL_HOURS = 24;

    private final AdminSessionRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Map<Long, AdminState> states = new HashMap<>();
    private final Map<Long, PendingMovie> pendingMovies = new HashMap<>();
    private final Set<Long> loaded = new HashSet<>();

    public AdminSessionManager(AdminSessionRepository repository) {
        this.repository = repository;
    }

    public AdminState getState(Long userId) {
        loadIfNeeded(userId);
        return states.getOrDefault(userId, AdminState.NONE);
    }

    public void setState(Long userId, AdminState state) {
        loadIfNeeded(userId);
        states.put(userId, state);
    }

    public PendingMovie getPendingMovie(Long userId) {
        loadIfNeeded(userId);
        return pendingMovies.computeIfAbsent(userId, k -> new PendingMovie());
    }

    public void clear(Long userId) {
        loaded.add(userId);
        states.remove(userId);
        pendingMovies.remove(userId);
        repository.deleteById(userId);
    }

    // update qayta ishlangandan keyin chaqiriladi
    public void persist(Long userId) {
        AdminState state = states.get(userId);

        if (state == null || state == AdminState.NONE) {
            if (states.containsKey(userId)) {
                clear(userId);
            }
            return;
        }

        try {
            PendingMovie pending = pendingMovies.get(userId);
            repository.save(AdminSession.builder()
                    .userId(userId)
                    .state(state)
                    .pendingJson(pending == null ? null : objectMapper.writeValueAsString(pending))
                    .updatedAt(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Admin sessiyasini saqlab bo'lmadi: userId=" + userId, e);
        }
    }

    private void loadIfNeeded(Long userId) {
        if (!loaded.add(userId)) return;

        repository.findById(userId).ifPresent(session -> {
            if (session.getUpdatedAt().isBefore(LocalDateTime.now().minusHours(SESSION_TTL_HOURS))) {
                repository.delete(session);
                return;
            }

            try {
                states.put(userId, session.getState());
                if (session.getPendingJson() != null) {
                    pendingMovies.put(userId, objectMapper.readValue(session.getPendingJson(), PendingMovie.class));
                }
            } catch (JsonProcessingException e) {
                // PendingMovie tuzilishi o'zgargan bo'lsa, eski sessiyani tashlab yuboramiz
                states.remove(userId);
                repository.delete(session);
            }
        });
    }
}
