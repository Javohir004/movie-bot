package com.moviebot.bot.service;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.domain.Season;
import com.moviebot.bot.repo.SeasonRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SeasonService {

    private final SeasonRepository seasonRepository;

    public SeasonService(SeasonRepository seasonRepository) {
        this.seasonRepository = seasonRepository;
    }

    public Season create(Movie movie, int seasonNumber) {
        Season season = Season.builder()
                .movie(movie)
                .seasonNumber(seasonNumber)
                .build();

        return seasonRepository.save(season);
    }

    public Season getById(Long id) {
        return seasonRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Season topilmadi: id=" + id));
    }

    public List<Season> getSeasonsForMovie(Long movieId) {
        return seasonRepository.findByMovieIdOrderBySeasonNumber(movieId);
    }
}