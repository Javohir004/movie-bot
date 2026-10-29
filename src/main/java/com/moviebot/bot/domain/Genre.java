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

    // admin yozgan asosiy nom; tarjima bo'lmasa shu ko'rsatiladi
    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "name_uz", length = 64)
    private String nameUz;

    @Column(name = "name_ru", length = 64)
    private String nameRu;

    @Column(name = "name_en", length = 64)
    private String nameEn;

    public String nameFor(String language) {
        return GenreNames.pick(name, nameUz, nameRu, nameEn, language);
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
