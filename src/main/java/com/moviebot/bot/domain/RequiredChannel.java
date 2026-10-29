package com.moviebot.bot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "required_channels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequiredChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ochiq kanal uchun, yopiq kanalda bo'sh
    @Column(name = "channel_username", unique = true)
    private String channelUsername;

    // yopiq kanal uchun (-100...), ochiq kanalda bo'sh
    @Column(name = "chat_id", unique = true)
    private String chatId;

    @Column(name = "title")
    private String title;

    // yopiq kanalga bot yaratgan havola
    @Column(name = "invite_link")
    private String inviteLink;

    @Builder.Default
    @Column(name = "join_request", nullable = false)
    private boolean joinRequest = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // bo'sh bo'lsa, muddatsiz
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // GetChatMember va boshqa so'rovlar uchun
    public String chatIdentifier() {
        return chatId != null ? chatId : "@" + channelUsername;
    }

    public String url() {
        return inviteLink != null ? inviteLink : "https://t.me/" + channelUsername;
    }

    public String displayName() {
        if (title != null && !title.isBlank()) return title;
        return channelUsername != null ? "@" + channelUsername : chatId;
    }
}
