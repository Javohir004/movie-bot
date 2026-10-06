package com.moviebot.bot.domain;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "channel_username", unique = true, nullable = false)
    private String channelUsername;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}