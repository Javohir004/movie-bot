package com.moviebot.bot.domain;

import com.moviebot.bot.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Column(name = "first_name")
    private String firstName;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // V3 dan oldin qo'shilgan foydalanuvchilarda bo'sh
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // uz, ru, en; bo'sh bo'lsa hali tanlamagan
    @Column(name = "language", length = 8)
    private String language;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastSeenAt == null) {
            lastSeenAt = createdAt;
        }
    }
}