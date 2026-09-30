package com.moviebot.bot.service;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.enums.MovieType;
import com.moviebot.bot.repo.MovieRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public Optional<Movie> findByCode(String code) {
        return movieRepository.findByCodeAndActiveTrue(code.trim());
    }

    public List<Movie> findByTitle(String title) {
        return movieRepository.findByTitleContainingIgnoreCaseAndActiveTrue(title.trim());
    }

    public List<Movie> findAll() {
        return movieRepository.findAllByActiveTrue();
    }

    public Movie getById(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Movie topilmadi: id=" + id));
    }

    public void save(String title, String code, MovieType type, String description, String fileId, String posterFileId) {
        Movie movie = Movie.builder()
                .title(title)
                .code(code)
                .type(type)
                .description(description)
                .fileId(fileId)
                .posterFileId(posterFileId)
                .active(true)
                .build();

        movieRepository.save(movie);
    }

    public Movie saveWithoutVideo(String title, String code, MovieType type, String description) {
        Movie movie = Movie.builder()
                .title(title)
                .code(code)
                .type(type)
                .description(description)
                .active(true)
                .build();

        return movieRepository.save(movie);
    }

    public void update(String originalCode, String title, String code, MovieType type, String description,
                       String fileId, String posterFileId) {
        Movie movie = movieRepository.findByCodeAndActiveTrue(originalCode)
                .orElseThrow(() -> new IllegalStateException("Movie topilmadi: " + originalCode));

        movie.setTitle(title);
        movie.setCode(code);
        movie.setType(type);
        movie.setDescription(description);

        if (fileId != null) {
            movie.setFileId(fileId);
        }
        if (posterFileId != null) {
            movie.setPosterFileId(posterFileId);
        }

        movieRepository.save(movie);
    }

    public void incrementViewCount(Movie movie) {
        movie.setViewCount(movie.getViewCount() + 1);
        movieRepository.save(movie);
    }

    public void deleteByCode(String code) {
        movieRepository.findByCodeAndActiveTrue(code).ifPresent(movie -> {
            movie.setActive(false);
            movieRepository.save(movie);
        });
    }

    public void updatePoster(Movie movie, String posterFileId) {
        movie.setPosterFileId(posterFileId);
        movieRepository.save(movie);
    }
}