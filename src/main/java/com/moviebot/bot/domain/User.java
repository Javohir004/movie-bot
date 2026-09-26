package com.moviebot.bot.domain;

import com.moviebot.bot.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    private Role role;

    public User(Long rootAdminId, Role role) {
        this.userId = rootAdminId;
        this.role = role;
    }
}