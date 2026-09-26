package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Movie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    Optional<Movie> findByCodeAndActiveTrue(String code);

    List<Movie> findByTitleContainingIgnoreCaseAndActiveTrue(String title);

    List<Movie> findAllByActiveTrue();
}
