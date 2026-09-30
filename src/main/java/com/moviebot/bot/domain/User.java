package com.moviebot.bot.domain;

import com.moviebot.bot.enums.Role;
import jakarta.persistence.*;
import lombok.*;

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
}