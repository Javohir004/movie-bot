package com.moviebot.bot.domain;

import com.moviebot.bot.enums.MovieType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Entity
@Table(name = "movies")
@Getter
@Setter
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    private MovieType type;

    private String description;

    @Column(name = "file_id")
    private String fileId;

    public Movie() {
    }


}