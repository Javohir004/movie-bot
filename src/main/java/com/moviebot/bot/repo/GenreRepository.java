package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {

    List<Genre> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    interface GenreWithCount {
        Long getId();

        String getName();

        Long getMovies();
    }

    // har bir janr va undagi faol kinolar soni
    @Query(value = "select g.id as id, g.name as name, count(m.id) as movies from genres g "
            + "left join movie_genres mg on mg.genre_id = g.id "
            + "left join movies m on m.id = mg.movie_id and m.is_active = true "
            + "group by g.id, g.name order by g.name", nativeQuery = true)
    List<GenreWithCount> findAllWithMovieCount();

    @Query(value = "select genre_id from movie_genres where movie_id = :movieId", nativeQuery = true)
    List<Long> findGenreIdsByMovie(@Param("movieId") Long movieId);

    @Query(value = "select g.name from genres g join movie_genres mg on mg.genre_id = g.id "
            + "where mg.movie_id = :movieId order by g.name", nativeQuery = true)
    List<String> findNamesByMovie(@Param("movieId") Long movieId);

    @Modifying
    @Query(value = "insert into movie_genres (movie_id, genre_id) values (:movieId, :genreId) on conflict do nothing", nativeQuery = true)
    void link(@Param("movieId") Long movieId, @Param("genreId") Long genreId);

    @Modifying
    @Query(value = "delete from movie_genres where movie_id = :movieId and genre_id = :genreId", nativeQuery = true)
    int unlink(@Param("movieId") Long movieId, @Param("genreId") Long genreId);
}
