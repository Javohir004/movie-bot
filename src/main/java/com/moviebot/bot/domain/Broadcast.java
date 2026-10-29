package com.moviebot.bot.domain;

import com.moviebot.bot.enums.BroadcastStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "broadcasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Broadcast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "source_chat_id", nullable = false)
    private Long sourceChatId;

    @Column(name = "source_message_id", nullable = false)
    private Integer sourceMessageId;

    @Column(name = "button_text", length = 64)
    private String buttonText;

    @Column(name = "button_url", length = 512)
    private String buttonUrl;

    @Column(name = "send_at", nullable = false)
    private LocalDateTime sendAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BroadcastStatus status;

    @Builder.Default
    @Column(nullable = false)
    private int total = 0;

    @Builder.Default
    @Column(nullable = false)
    private int sent = 0;

    @Builder.Default
    @Column(nullable = false)
    private int blocked = 0;

    @Builder.Default
    @Column(nullable = false)
    private int failed = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
