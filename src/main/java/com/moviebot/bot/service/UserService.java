package com.moviebot.bot.service;


import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    public void registerOrUpdateName(Long userId, String firstName) {
        userRepository.findByUserId(userId).ifPresentOrElse(
                user -> {
                    boolean changed = false;
                    if (firstName != null && !firstName.equals(user.getFirstName())) {
                        user.setFirstName(firstName);
                        changed = true;
                    }
                    // botni blokdan chiqarib qayta yozgan foydalanuvchi
                    if (!user.isActive()) {
                        user.setActive(true);
                        changed = true;
                    }
                    if (changed) {
                        userRepository.save(user);
                    }
                },
                () -> {
                    User user = User.builder()
                            .userId(userId)
                            .firstName(firstName)
                            .role(Role.USER)
                            .build();
                    userRepository.save(user);
                }
        );
    }

    public List<User> getAllAdmins() {
        return userRepository.findByRole(Role.ADMIN);
    }

    public void makeUser(Long userId) {
        userRepository.findByUserId(userId).ifPresent(user -> {
            user.setRole(Role.USER);
            userRepository.save(user);
        });
    }

    public Optional<User> findByUserId(Long userId) {
        return userRepository.findByUserId(userId);
    }

    public List<Long> getAllActiveUserIds() {
        return userRepository.findByActiveTrue().stream()
                .map(User::getUserId)
                .toList();
    }

    // botni bloklagan yoki akkauntini o'chirgan foydalanuvchilar reklamada qayta-qayta "xato" bo'lmasin
    public void deactivate(Collection<Long> userIds) {
        List<Long> ids = List.copyOf(userIds);

        // PostgreSQL IN (...) parametrlari soni cheklangan, shuning uchun bo'laklab ishlaymiz
        for (int from = 0; from < ids.size(); from += 1000) {
            List<User> users = userRepository.findByUserIdIn(ids.subList(from, Math.min(from + 1000, ids.size())));
            users.forEach(user -> user.setActive(false));
            userRepository.saveAll(users);
        }
    }
}