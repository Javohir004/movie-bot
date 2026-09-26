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
        return movieRepository.findByCode(code.trim());
    }

    public List<Movie> findByTitle(String title) {
        return movieRepository.findByTitleContainingIgnoreCase(title.trim());
    }

    public List<Movie> findAll() {
        return movieRepository.findAll();
    }

    public void save(String title, String code, MovieType type, String description, String fileId) {
        Movie movie = new Movie();
        movie.setTitle(title);
        movie.setCode(code);
        movie.setType(type);
        movie.setDescription(description);
        movie.setFileId(fileId);
        movieRepository.save(movie);
    }

    public void update(String originalCode, String title, String code, MovieType type, String description, String fileId) {
        Movie movie = movieRepository.findByCode(originalCode)
                .orElseThrow(() -> new IllegalStateException("Movie topilmadi: " + originalCode));

        movie.setTitle(title);
        movie.setCode(code);
        movie.setType(type);
        movie.setDescription(description);

        if (fileId != null) {
            movie.setFileId(fileId);
        }

        movieRepository.save(movie);
    }

    public void deleteByCode(String code) {
        movieRepository.findByCode(code).ifPresent(movieRepository::delete);
    }
}
