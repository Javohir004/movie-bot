package com.moviebot.bot.service;


import com.moviebot.bot.domain.User;
import com.moviebot.bot.enums.Role;
import com.moviebot.bot.repo.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

@Service
public class UserService {

    // last_seen_at har tugma bosilganda emas, shu oraliqda bir marta yoziladi
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final Map<Long, LocalDateTime> lastTouched = new ConcurrentHashMap<>();
    // har xabarda bazadan o'qimaslik uchun; Optional.empty() = hali tanlamagan
    private final Map<Long, Optional<String>> languages = new ConcurrentHashMap<>();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // foydalanuvchi botdan foydalanganini belgilaydi (statistikadagi "faol" hisobi uchun)
    public void touch(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previous = lastTouched.get(userId);
        if (previous != null && previous.isAfter(now.minus(TOUCH_INTERVAL))) {
            return;
        }
        lastTouched.put(userId, now);

        userRepository.findByUserId(userId).ifPresent(user -> {
            user.setLastSeenAt(now);
            user.setActive(true);
            userRepository.save(user);
        });
    }

    // tanlangan til yoki bo'sh (hali tanlamagan / foydalanuvchi bazada yo'q)
    public Optional<String> findLanguage(Long userId) {
        return languages.computeIfAbsent(userId, id ->
                userRepository.findByUserId(id).map(User::getLanguage).filter(l -> !l.isBlank()));
    }

    public void setLanguage(Long userId, String language) {
        userRepository.findByUserId(userId).ifPresent(user -> {
            user.setLanguage(language);
            userRepository.save(user);
        });
        languages.put(userId, Optional.of(language));
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