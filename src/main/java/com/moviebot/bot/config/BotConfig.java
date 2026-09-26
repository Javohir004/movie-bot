package com.moviebot.bot.config;

import com.moviebot.bot.MovieBot;
import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


@Component
public class BotConfig {

    private final MovieBot movieBot;
    private final UserRepository userRepository;
    private final Long rootAdminId;

    public BotConfig(MovieBot movieBot,
                     UserRepository userRepository,
                     @Value("${bot.root-admin-id}") Long rootAdminId) {
        this.movieBot = movieBot;
        this.userRepository = userRepository;
        this.rootAdminId = rootAdminId;
    }

    @PostConstruct
    public void init() {
        registerBot();
        ensureRootAdmin();
    }

    private void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(movieBot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void ensureRootAdmin() {
        userRepository.findByUserId(rootAdminId).ifPresentOrElse(
                user -> {
                    if (user.getRole() != Role.ADMIN) {
                        user.setRole(Role.ADMIN);
                        userRepository.save(user);
                    }
                },
                () -> userRepository.save(new User(rootAdminId, Role.ADMIN))
        );
    }
}
