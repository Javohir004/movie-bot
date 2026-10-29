package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.enums.MovieType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    Optional<Movie> findByCodeAndActiveTrue(String code);

    List<Movie> findByTitleContainingIgnoreCaseAndActiveTrue(String title);

    List<Movie> findAllByActiveTrue();

    boolean existsByCode(String code);

    long countByTypeAndActiveTrue(MovieType type);

    // kinolar uchun video ko'rishlar, seriallar uchun fasl ochilishlar (Top 5 dagi hisob bilan bir xil)
    @Query("select coalesce(sum(m.viewCount), 0) from Movie m where m.active = true and m.type = com.moviebot.bot.enums.MovieType.MOVIE")
    long sumMovieViews();

    @Query("select coalesce(sum(s.viewCount), 0) from Season s where s.movie.active = true")
    long sumSeasonViews();
}
