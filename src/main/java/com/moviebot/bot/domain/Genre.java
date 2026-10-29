package com.moviebot.bot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Kino bilan bog'lanish movie_genres jadvalida, GenreRepository'dagi so'rovlar orqali.
// Movie ichida @ManyToMany qilinmadi: bot tranzaksiyadan tashqarida ishlaydi va lazy yuklash xato beradi.
@Entity
@Table(name = "genres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
