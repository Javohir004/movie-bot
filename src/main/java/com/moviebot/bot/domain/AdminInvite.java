package com.moviebot.bot.domain;

import com.moviebot.bot.enums.InviteStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_invites")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @Column(name = "created_by", nullable = false)
    private Long createdByUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "target_name")
    private String targetName;

    @Enumerated(EnumType.STRING)
    private InviteStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}