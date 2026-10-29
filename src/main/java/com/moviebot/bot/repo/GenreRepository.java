package com.moviebot.bot.repo;

import com.moviebot.bot.domain.Genre;
import com.moviebot.bot.domain.GenreNames;
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

        String getNameUz();

        String getNameRu();

        String getNameEn();

        Long getMovies();

        default String nameFor(String language) {
            return GenreNames.pick(getName(), getNameUz(), getNameRu(), getNameEn(), language);
        }
    }

    // har bir janr va undagi faol kinolar soni.
    // Taxalluslar qo'shtirnoqda: aks holda PostgreSQL ularni kichik harfga o'tkazadi (nameuz) va proyeksiya topa olmaydi.
    @Query(value = "select g.id as \"id\", g.name as \"name\", g.name_uz as \"nameUz\", g.name_ru as \"nameRu\", "
            + "g.name_en as \"nameEn\", count(m.id) as \"movies\" from genres g "
            + "left join movie_genres mg on mg.genre_id = g.id "
            + "left join movies m on m.id = mg.movie_id and m.is_active = true "
            + "group by g.id, g.name, g.name_uz, g.name_ru, g.name_en order by g.name", nativeQuery = true)
    List<GenreWithCount> findAllWithMovieCount();

    @Query(value = "select genre_id from movie_genres where movie_id = :movieId", nativeQuery = true)
    List<Long> findGenreIdsByMovie(@Param("movieId") Long movieId);

    @Query(value = "select g.* from genres g join movie_genres mg on mg.genre_id = g.id "
            + "where mg.movie_id = :movieId order by g.name", nativeQuery = true)
    List<Genre> findByMovie(@Param("movieId") Long movieId);

    @Modifying
    @Query(value = "insert into movie_genres (movie_id, genre_id) values (:movieId, :genreId) on conflict do nothing", nativeQuery = true)
    void link(@Param("movieId") Long movieId, @Param("genreId") Long genreId);

    @Modifying
    @Query(value = "delete from movie_genres where movie_id = :movieId and genre_id = :genreId", nativeQuery = true)
    int unlink(@Param("movieId") Long movieId, @Param("genreId") Long genreId);
}
