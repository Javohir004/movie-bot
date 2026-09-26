package com.moviebot.bot.service;


import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void registerIfAbsent(Long userId) {
        if (userRepository.findByUserId(userId).isEmpty()) {
            User user = User.builder()
                    .userId(userId)
                    .role(Role.USER)
                    .build();

            userRepository.save(user);
        }
    }

    public boolean isAdmin(Long userId) {
        return userRepository.existsByUserIdAndRole(userId, Role.ADMIN);
    }

    public void makeAdmin(Long userId) {
        userRepository.findByUserId(userId).ifPresentOrElse(
                user -> {
                    user.setRole(Role.ADMIN);
                    userRepository.save(user);
                },
                () -> {
                    User newAdmin = User.builder()
                            .userId(userId)
                            .role(Role.ADMIN)
                            .build();
                    userRepository.save(newAdmin);
                }
        );
    }
}