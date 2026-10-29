package com.moviebot.bot.domain;

import com.moviebot.bot.enums.AdminState;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminSession {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminState state;

    // PendingMovie JSON ko'rinishida
    @Column(name = "pending_json", columnDefinition = "TEXT")
    private String pendingJson;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
