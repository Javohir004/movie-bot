package com.moviebot.bot.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "seasons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @Column(name = "season_number", nullable = false)
    private Integer seasonNumber;

    @Column(name = "poster_file_id")
    private String posterFileId;

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Builder.Default
    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Episode> episodes = new ArrayList<>();
}