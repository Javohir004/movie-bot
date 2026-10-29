package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Movie;
import com.moviebot.bot.enums.MovieType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends JpaRepository<Movie, Long> {

    Optional<Movie> findByCodeAndActiveTrue(String code);

    List<Movie> findByTitleContainingIgnoreCaseAndActiveTrue(String title);

    List<Movie> findAllByActiveTrue();

    boolean existsByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query(value = "select m.* from movies m join movie_genres mg on mg.movie_id = m.id "
            + "where mg.genre_id = :genreId and m.is_active = true order by m.title", nativeQuery = true)
    List<Movie> findActiveByGenre(@Param("genreId") Long genreId);

    Optional<Movie> findFirstByCodeIgnoreCaseAndActiveTrueOrderByIdAsc(String code);

    // Nomni kichik harflarga o'tkazib, turli apostroflarni bitta (') ga keltirib qidiradi.
    // q ham xuddi shunday normallashtirilgan bo'lishi kerak (MovieService.normalize).
    @Query(value = "select * from movies m where m.is_active = true "
            + "and strpos(translate(lower(m.title), :apostrophes, :plain), :q) > 0 "
            + "order by m.title limit :limit", nativeQuery = true)
    List<Movie> searchByNormalizedTitle(@Param("q") String q, @Param("apostrophes") String apostrophes,
                                        @Param("plain") String plain, @Param("limit") int limit);

    // Xato yozilgan nomlar uchun o'xshashlari (pg_trgm kerak)
    @Query(value = "select m.* from movies m where m.is_active = true "
            + "and greatest(similarity(translate(lower(m.title), :apostrophes, :plain), :q), "
            + "             word_similarity(:q, translate(lower(m.title), :apostrophes, :plain))) >= :threshold "
            + "order by greatest(similarity(translate(lower(m.title), :apostrophes, :plain), :q), "
            + "                  word_similarity(:q, translate(lower(m.title), :apostrophes, :plain))) desc "
            + "limit :limit", nativeQuery = true)
    List<Movie> findSimilarTitles(@Param("q") String q, @Param("apostrophes") String apostrophes,
                                  @Param("plain") String plain, @Param("threshold") double threshold,
                                  @Param("limit") int limit);

    long countByTypeAndActiveTrue(MovieType type);

    // kinolar uchun video ko'rishlar, seriallar uchun fasl ochilishlar (Top 5 dagi hisob bilan bir xil)
    @Query("select coalesce(sum(m.viewCount), 0) from Movie m where m.active = true and m.type = com.moviebot.bot.enums.MovieType.MOVIE")
    long sumMovieViews();

    @Query("select coalesce(sum(s.viewCount), 0) from Season s where s.movie.active = true")
    long sumSeasonViews();
}
